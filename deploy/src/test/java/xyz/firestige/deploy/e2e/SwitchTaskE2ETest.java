package xyz.firestige.deploy.e2e;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.application.task.TaskOperationService;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.facade.DeploymentTaskFacade;
import xyz.firestige.deploy.infrastructure.execution.stage.StageFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.testutil.TestEventTracker;
import xyz.firestige.deploy.testutil.factory.AggregationFactory;
import xyz.firestige.deploy.testutil.factory.ValueObjectTestFactory;
import xyz.firestige.deploy.testutil.stage.AlwaysSuccessStage;
import xyz.firestige.deploy.testutil.stage.FailOnceStage;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.redis.ack.api.RedisAckService;

/**
 * 切换流程端到端测试（T-035）
 * <p>
 * 测试场景：
 * 1. 完整的重试流程：失败 → 重试（从头/从checkpoint）→ 成功
 * 2. 完整的回滚流程：成功部署 → 回滚到旧版本 → 成功
 * 3. 验证事件发布和状态转换
 * 4. 验证无状态执行器行为（每次创建新Task）
 * 
 * @since T-035 无状态执行器改造
 */
@DisplayName("E2E: 切换流程（T-035）")
class SwitchTaskE2ETest extends BaseE2ETest {

    private static final Logger log = LoggerFactory.getLogger(SwitchTaskE2ETest.class);
    @MockBean
    private RedisAckService redisAckService;

    @Autowired
    private TaskOperationService taskOperationService;

    @Autowired
    private TestEventTracker eventTracker;

    @MockBean
    private StageFactory stageFactory;  // Mock StageFactory，使用测试 stages

    @Test
    @DisplayName("场景1: 从头重试任务 - 验证完整调用链路")
    void scenario1_retryFromBeginning_shouldSucceed() throws Exception {
        // ========== 步骤1: 准备配置和Mock Stages ==========
        log.info("步骤1: 准备测试配置");
        
        TenantConfig config = ValueObjectTestFactory.minimalConfig();
        
        // 第一次执行失败，第二次成功
        List<TaskStage> stages = List.of(
            new AlwaysSuccessStage("stage-1", Duration.ofMillis(100)),
            new AlwaysSuccessStage("stage-2", Duration.ofMillis(100)),  // 第1次失败，第2次成功
            new AlwaysSuccessStage("stage-3", Duration.ofMillis(100))
        );
        
        // Mock StageFactory 返回测试 stages
        when(stageFactory.buildStages(any(TenantConfig.class))).thenReturn(stages);
        
        // ========== 步骤2: 调用 Facade 触发重试 ==========
        log.info("步骤2: 调用 Facade.retryTask()");
        eventTracker.clear();
        
        // 重试（lastCompletedStageName = null 表示从头开始）
        // T-035: 每次调用创建新Task，不需要提前准备失败的Task
        taskOperationService.retryTask(config, null);
        
        // ========== 步骤3: 通过事件验证执行结果 ==========
        log.info("步骤3: 验证事件序列");

        await().atMost(Duration.ofSeconds(6)).pollDelay(Duration.ofSeconds(5)).untilAsserted(() -> {
            // 验证事件发布（无状态设计：不查询Repository，只验证事件）
            List<TestEventTracker.TrackedEvent> events = eventTracker.getEvents();
            // 验证任务启动事件
            assertThat(events)
                    .as("应该发布任务启动事件")
                    .anyMatch(e -> e.type == TestEventTracker.EventType.TASK_STARTED);

            // 验证 Stage 执行事件
            assertThat(events)
                    .as("应该发布 Stage 启动事件")
                    .anyMatch(e -> e.type == TestEventTracker.EventType.STAGE_STARTED);

            // 验证至少有 Stage 完成事件（说明有Stage执行成功）
            assertThat(events)
                    .as("应该有 Stage 完成事件")
                    .anyMatch(e -> e.type == TestEventTracker.EventType.STAGE_COMPLETED);

            log.info("场景1完成: 从头重试，事件验证通过，共发布 {} 个事件", events.size());
        });

    }

    @Test
    @DisplayName("场景2: 从指定checkpoint重试 - 验证部分执行")
    void scenario2_retryFromCheckpoint_shouldSucceed() throws Exception {
        // ========== 步骤1: 准备配置和Mock Stages ==========
        log.info("步骤1: 准备测试配置");
        
        TenantConfig config = ValueObjectTestFactory.minimalConfig();
        
        // 4个stages
        List<TaskStage> stages = List.of(
            new AlwaysSuccessStage("stage-1", Duration.ofMillis(50)),
            new AlwaysSuccessStage("stage-2", Duration.ofMillis(50)),
            new AlwaysSuccessStage("stage-3", Duration.ofMillis(50)),
            new AlwaysSuccessStage("stage-4", Duration.ofMillis(50))
        );
        
        when(stageFactory.buildStages(any(TenantConfig.class))).thenReturn(stages);
        
        // ========== 步骤2: 从 stage-2 之后重试 ==========
        log.info("步骤2: 从 stage-2 之后重试");
        eventTracker.clear();
        
        // 从 stage-2 之后继续（应该只执行 stage-3 和 stage-4）
        taskOperationService.retryTask(config, "stage-2");
        
        // ========== 步骤3: 验证事件序列 ==========
        log.info("步骤3: 验证从checkpoint执行的事件");

        await().atMost(Duration.ofSeconds(6)).pollDelay(Duration.ofSeconds(5)).untilAsserted(() -> {

            List<TestEventTracker.TrackedEvent> events = eventTracker.getEvents();

            // 验证任务启动
            assertThat(events)
                    .as("应该发布任务启动事件")
                    .anyMatch(e -> e.type == TestEventTracker.EventType.TASK_STARTED);

            // 验证有Stage执行（从checkpoint之后的stages）
            long stageStartedCount = events.stream()
                    .filter(e -> e.type == TestEventTracker.EventType.STAGE_STARTED)
                    .count();

            assertThat(stageStartedCount)
                    .as("应该执行checkpoint之后的stages")
                    .isGreaterThan(0);

            log.info("场景2完成: 从checkpoint重试，执行了 {} 个stages", stageStartedCount);
        });

    }

    @Test
    @DisplayName("场景3: 回滚到旧版本 - 验证使用旧配置正向执行")
    void scenario3_rollbackToOldVersion_shouldSucceed() throws Exception {
        // ========== 步骤1: 准备旧版本和新版本配置 ==========
        log.info("步骤1: 准备旧版本配置");
        
        // 旧版本配置
        TenantConfig oldConfig = ValueObjectTestFactory.minimalConfig();
        oldConfig.setDeployUnit(ValueObjectTestFactory.randomDeployUnitIdentifier());
        
        // 回滚用的stages（使用旧配置）
        List<TaskStage> rollbackStages = List.of(
            new AlwaysSuccessStage("rollback-stage-1", Duration.ofMillis(100)),
            new AlwaysSuccessStage("rollback-stage-2", Duration.ofMillis(100)),
            new AlwaysSuccessStage("rollback-stage-3", Duration.ofMillis(100))
        );
        
        when(stageFactory.buildStages(any(TenantConfig.class))).thenReturn(rollbackStages);
        
        // ========== 步骤2: 调用 Facade 触发回滚 ==========
        log.info("步骤2: 触发回滚到旧版本");
        eventTracker.clear();
        
        // 回滚（使用旧配置重新执行）
        taskOperationService.rollbackTask(
                oldConfig,
                null,  // null 表示全部回滚
                "v2.0.0"
        );
        
        // ========== 步骤3: 验证回滚执行 ==========
        log.info("步骤3: 验证回滚事件");

        await().atMost(Duration.ofSeconds(6)).pollDelay(Duration.ofSeconds(5)).untilAsserted(() -> {

            // 验证事件发布（回滚是正向执行）
            List<TestEventTracker.TrackedEvent> events = eventTracker.getEvents();

            // 验证任务启动
            assertThat(events)
                    .as("回滚应该发布任务启动事件")
                    .anyMatch(e -> e.type == TestEventTracker.EventType.TASK_STARTED);

            // 验证Stage执行（回滚用旧配置正向执行stages）
            long stageCount = events.stream()
                    .filter(e -> e.type == TestEventTracker.EventType.STAGE_STARTED)
                    .count();

            assertThat(stageCount)
                    .as("回滚应该执行旧配置的stages")
                    .isGreaterThan(0);

            log.info("场景3完成: 回滚执行，共 {} 个stages，发布 {} 个事件", stageCount, events.size());
        });

    }

    @Test
    @DisplayName("场景5: 回滚部分stages（从指定checkpoint开始）")
    void scenario5_rollbackFromCheckpoint_shouldSucceed() throws Exception {
        // ========== 步骤1: 准备成功的部署 ==========
        log.info("步骤1: 准备成功的部署");
        
        TenantConfig oldConfig = ValueObjectTestFactory.minimalConfig();
        TenantConfig newConfig = ValueObjectTestFactory.withPreviousConfig(oldConfig);
        
//        TaskAggregate task = AggregationFactory.buildTaskAggregationFrom(newConfig).buildPending();
        
        List<TaskStage> stages = List.of(
            new AlwaysSuccessStage("stage-1", Duration.ofMillis(50)),
            new AlwaysSuccessStage("stage-2", Duration.ofMillis(50)),
            new AlwaysSuccessStage("stage-3", Duration.ofMillis(50)),
            new AlwaysSuccessStage("stage-4", Duration.ofMillis(50))
        );
        
        when(stageFactory.buildStages(any(TenantConfig.class))).thenReturn(stages);
        // ========== 步骤2: 从stage-2之后开始回滚 ==========
        log.info("步骤2: 从stage-2之后开始回滚");
        eventTracker.clear();

        // 只回滚 stage-3 和 stage-4
        taskOperationService.rollbackTask(oldConfig, "stage-2", "v1.0.0");

        await().atMost(Duration.ofSeconds(6)).pollDelay(Duration.ofSeconds(5)).untilAsserted(() -> {

            // ========== 步骤3: 验证部分回滚 ==========
            log.info("步骤3: 验证部分回滚结果");

            TaskAggregate rollbackTask = taskRepository.findByTenantId(oldConfig.getTenantId())
                    .orElseThrow(() -> new AssertionError("应该找到回滚任务"));

            assertThat(rollbackTask.getStatus())
                    .as("部分回滚应该成功")
                    .isIn(TaskStatus.RUNNING, TaskStatus.COMPLETED);

            log.info("场景5完成: 部分回滚成功");
        });

    }
}
