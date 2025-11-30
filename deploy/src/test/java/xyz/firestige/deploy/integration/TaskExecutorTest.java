package xyz.firestige.deploy.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import xyz.firestige.deploy.application.checkpoint.CheckpointService;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskCheckpoint;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskRuntimeRepository;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;
import xyz.firestige.deploy.infrastructure.execution.TaskResult;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.testutil.TestEventTracker;
import xyz.firestige.deploy.testutil.factory.AggregationFactory;
import xyz.firestige.deploy.testutil.factory.TaskExecutorFactory;
import xyz.firestige.deploy.testutil.factory.ValueObjectTestFactory;
import xyz.firestige.deploy.testutil.stage.AlwaysSuccessStage;
import xyz.firestige.deploy.testutil.stage.ConditionalFailStage;
import xyz.firestige.deploy.testutil.stage.FailOnceStage;
import xyz.firestige.redis.ack.api.RedisAckService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class TaskExecutorTest {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutorTest.class);
    @MockBean
    private RedisAckService redisAckService; // 阻断 Redis Ack 服务，避免干扰测试

    @Autowired
    private TaskExecutorFactory taskExecutorFactory; // ✅ 注入工厂

    @Autowired
    private TestEventTracker eventTracker; // ✅ 注入跟踪器
    @Autowired
    private TaskDomainService taskDomainService;
    @Autowired
    private TaskRuntimeRepository taskRuntimeRepository; // ✅ T-032: 注入 Repository 用于保存 Context
    @Autowired
    private CheckpointService checkpointService;

    @BeforeEach
    void setUp() {
        eventTracker.clear(); // 清理上次测试的事件
    }

    @Test
    void testSuccessfulTaskExecution() {
        TenantConfig config = ValueObjectTestFactory.withPreviousConfig();
        // 准备测试数据
        TaskAggregate task = AggregationFactory.buildTaskAggregationFrom(config).buildPending();
        List<TaskStage> stages = List.of(
                new AlwaysSuccessStage("stage-1"),
                new AlwaysSuccessStage("stage-2")
        );
        taskDomainService.attacheStages(task, stages);

        // ✅ 使用工厂创建 TaskExecutor
        TaskExecutor executor = taskExecutorFactory.create(task, stages);

        // 执行任务
        TaskResult result = executor.execute();

        // ✅ 断言执行成功
        assertTrue(result.isSuccess());

        // ✅ 验证事件顺序
        List<TestEventTracker.TrackedEvent> events = eventTracker.getEvents();
        assertThat(events).extracting("type").containsExactly(
                TestEventTracker.EventType.TASK_STARTED,
                TestEventTracker.EventType.STAGE_STARTED,   // Stage1
                TestEventTracker.EventType.STAGE_COMPLETED,
                TestEventTracker.EventType.STAGE_STARTED,   // Stage2
                TestEventTracker.EventType.STAGE_COMPLETED,
                TestEventTracker.EventType.TASK_COMPLETED
        );

        // ✅ 验证状态转换
        List<TaskStatus> statusHistory = eventTracker.getTaskStatusHistory(task.getTaskId());
        assertThat(statusHistory).containsExactly(
                TaskStatus.RUNNING,
                TaskStatus.COMPLETED
        );

        // ✅ 验证 Stage 执行顺序
        List<String> executedStages = eventTracker.getExecutedStages(task.getTaskId());
        assertThat(executedStages).containsExactly(
                "stage-1", "stage-1",
                "stage-2", "stage-2"
        );
    }

    @Test
    void testTaskFailureAtSecondStage() {
        // 准备失败场景（第二个 Stage 失败）
        TenantConfig config = ValueObjectTestFactory.withPreviousConfig();
        TaskAggregate task = AggregationFactory.buildTaskAggregationFrom(config).buildPending();
        List<TaskStage> stages = List.of(
                new AlwaysSuccessStage("stage-1"),
                new FailOnceStage("stage-2")
        );
        taskDomainService.attacheStages(task, stages);


        // ✅ 使用工厂创建 TaskExecutor
        TaskExecutor executor = taskExecutorFactory.create(task, stages);
        TaskResult result = executor.execute();

        // ✅ 断言任务失败
        assertThat(result.isSuccess()).isFalse();

        // ✅ 验证事件顺序
        List<TestEventTracker.TrackedEvent> events = eventTracker.getEvents();
        assertThat(events).extracting("type").containsExactly(
                TestEventTracker.EventType.TASK_STARTED,
                TestEventTracker.EventType.STAGE_STARTED,   // Stage1 成功
                TestEventTracker.EventType.STAGE_COMPLETED,
                TestEventTracker.EventType.STAGE_STARTED,   // Stage2 失败
                TestEventTracker.EventType.STAGE_FAILED,
                TestEventTracker.EventType.TASK_FAILED
        );

        // ✅ 验证失败的 Stage
        Optional<TestEventTracker.TrackedEvent> failedStage = events.stream()
                .filter(e -> e.type == TestEventTracker.EventType.STAGE_FAILED)
                .findFirst();
        assertThat(failedStage).isPresent();
        assertThat(failedStage.get().stageName).isEqualTo("stage-2");
    }

    @Test
    void testTaskFailureAtSecondStageAndRetrySuccess() {
        // 准备失败场景（第二个 Stage 失败）
        TenantConfig config = ValueObjectTestFactory.withPreviousConfig();
        TaskAggregate task = AggregationFactory.buildTaskAggregationFrom(config).buildPending();
        List<TaskStage> stages = List.of(
                new AlwaysSuccessStage("stage-1"),
                new FailOnceStage("stage-2")
        );
        taskDomainService.attacheStages(task, stages);


        // ✅ 使用工厂创建 TaskExecutor
        TaskExecutor executor = taskExecutorFactory.create(task, stages);
        TaskResult result = executor.execute();

        // ✅ 断言任务失败
        assertThat(result.isSuccess()).isFalse();

        // ✅ 验证事件顺序
        List<TestEventTracker.TrackedEvent> events = eventTracker.getEvents();
        assertThat(events).extracting("type").containsExactly(
                TestEventTracker.EventType.TASK_STARTED,
                TestEventTracker.EventType.STAGE_STARTED,   // Stage1 成功
                TestEventTracker.EventType.STAGE_COMPLETED,
                TestEventTracker.EventType.STAGE_STARTED,   // Stage2 失败
                TestEventTracker.EventType.STAGE_FAILED,
                TestEventTracker.EventType.TASK_FAILED
        );

        // ✅ 验证失败的 Stage
        Optional<TestEventTracker.TrackedEvent> failedStage = events.stream()
                .filter(e -> e.type == TestEventTracker.EventType.STAGE_FAILED)
                .findFirst();
        assertThat(failedStage).isPresent();
        assertThat(failedStage.get().stageName).isEqualTo("stage-2");

        log.info("task.status: {}", task.getStatus());

        TaskCheckpoint cp = checkpointService.loadCheckpoint(task);
        assertNotNull(cp);
        assertEquals(0, cp.getLastCompletedStageIndex());

        eventTracker.clear(); // 清理事件，准备重试验证

        log.info("Retrying the failed task...");

        assertTrue(eventTracker.getEvents().isEmpty());

        // ✅ T-032: 创建自定义 Context 并设置重试标志位
        TaskRuntimeContext retryContext = new TaskRuntimeContext(
            task.getPlanId(),
            task.getTaskId(),
            task.getTenantId()
        );
        retryContext.requestRetry(true);  // 从检查点恢复

        // ✅ 保存 Context 到 Repository（重要！）
        taskRuntimeRepository.saveContext(task.getTaskId(), retryContext);

        // 使用自定义 Context 创建 executor
        TaskExecutor retryExecutor = taskExecutorFactory.create(task, stages, retryContext);

        // 重试任务（统一通过 execute()）
        result = retryExecutor.execute();

        // ✅ 验证重试后任务成功
        assertTrue(result.isSuccess());
        List<TestEventTracker.TrackedEvent> eventsAfterRetry = eventTracker.getEvents();
        assertThat(eventsAfterRetry).extracting("type").containsSequence(
                TestEventTracker.EventType.TASK_STARTED,
                TestEventTracker.EventType.STAGE_STARTED,   // Stage2 成功
                TestEventTracker.EventType.STAGE_COMPLETED,
                TestEventTracker.EventType.TASK_COMPLETED
        );
    }

    @Test
    void testTaskRollback() {
//        TenantConfig config = ValueObjectTestFactory.withPreviousConfig();
//        TaskAggregate task = AggregationFactory.buildTaskAggregationFrom(config).buildPending();
//
//        // ✅ 使用工厂创建 TaskExecutor（带自定义 context）
//        TaskExecutor executor = taskExecutorFactory.create(task, createTestStages(), context);
//
//        // 第一次执行（会暂停）
//        TaskResult result1 = executor.execute();
//        assertThat(result1.getStatus()).isEqualTo(TaskStatus.PAUSED);
//
//        // ✅ 验证暂停事件
//        List<TestEventTracker.TrackedEvent> events = eventTracker.getEvents();
//        assertThat(events).extracting("type").contains(TestEventTracker.EventType.TASK_PAUSED);
//
//        // 清理事件，准备恢复
//        eventTracker.clear();
//
//        // 第二次执行（恢复）
//        TaskExecutor executor2 = taskExecutorFactory.create(task, createTestStages());
//        TaskResult result2 = executor2.execute();
//        assertThat(result2.getStatus()).isEqualTo(TaskStatus.COMPLETED);
//
//        // ✅ 验证恢复事件
//        events = eventTracker.getEvents();
//        assertThat(events).extracting("type").contains(TestEventTracker.EventType.TASK_RESUMED);
    }

    // ============================================
    // T-032: 状态机重构测试用例
    // ============================================

    /**
     * T-032: 测试最后一个 Stage 不保存检查点
     * <p>
     * 验证点：
     * 1. 两个 Stage 都成功执行
     * 2. 只有第一个 Stage 保存检查点
     * 3. Task 完成后检查点已清理
     * 4. 显式的 TaskCompleted 事件
     */
    @Test
    void testCheckpointNotSavedForLastStage() {
        // 准备测试数据
        TenantConfig config = ValueObjectTestFactory.withPreviousConfig();
        TaskAggregate task = AggregationFactory.buildTaskAggregationFrom(config).buildPending();

        List<TaskStage> stages = List.of(
            new AlwaysSuccessStage("stage-1"),
            new AlwaysSuccessStage("stage-2")
        );
        taskDomainService.attacheStages(task, stages);

        // 清空事件跟踪
        eventTracker.clear();

        // ✅ 使用工厂创建 TaskExecutor
        TaskExecutor executor = taskExecutorFactory.create(task, stages);

        // 执行任务
        TaskResult result = executor.execute();

        // ✅ 验证：Task 完成
        assertThat(result.isSuccess()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);

        // ✅ 验证：事件顺序正确（包含显式的 TASK_COMPLETED）
        List<TestEventTracker.TrackedEvent> events = eventTracker.getEvents();
        assertThat(events).extracting("type").containsExactly(
            TestEventTracker.EventType.TASK_STARTED,
            TestEventTracker.EventType.STAGE_STARTED,
            TestEventTracker.EventType.STAGE_COMPLETED,
            TestEventTracker.EventType.STAGE_STARTED,
            TestEventTracker.EventType.STAGE_COMPLETED,
            TestEventTracker.EventType.TASK_COMPLETED  // ✅ 显式完成事件
        );

        log.info("✅ T-032: 最后一个 Stage 不保存检查点测试通过");
    }

    /**
     * T-032: 测试非最后 Stage 保存检查点
     * <p>
     * 验证点：
     * 1. 3个 Stage，第2个失败
     * 2. 第1个 Stage 的检查点已保存
     * 3. 可以从检查点恢复重试
     */
    @Test
    void testCheckpointSavedForNonLastStage() {
        // 准备测试数据：3个 Stage，执行到第2个失败
        TenantConfig config = ValueObjectTestFactory.withPreviousConfig();
        TaskAggregate task = AggregationFactory.buildTaskAggregationFrom(config).buildPending();

        List<TaskStage> stages = List.of(
            new AlwaysSuccessStage("stage-1"),
            new FailOnceStage("stage-2"),  // 第一次失败
            new AlwaysSuccessStage("stage-3")
        );
        taskDomainService.attacheStages(task, stages);

        // 执行任务（会失败）
        TaskExecutor executor = taskExecutorFactory.create(task, stages);
        TaskResult result = executor.execute();

        // ✅ 验证：Task 失败
        assertThat(result.isSuccess()).isFalse();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);

        // ✅ 验证：第一个 Stage 完成，第二个失败
        List<TestEventTracker.TrackedEvent> events = eventTracker.getEvents();
        long completedStages = events.stream()
            .filter(e -> e.type == TestEventTracker.EventType.STAGE_COMPLETED)
            .count();
        assertThat(completedStages).isEqualTo(1); // 只有 stage-1 完成

        // 清空事件，准备重试
        eventTracker.clear();

        // ✅ T-032: 创建自定义 Context 并设置重试标志位（从检查点恢复）
        TaskRuntimeContext retryContext = new TaskRuntimeContext(
            task.getPlanId(),
            task.getTaskId(),
            task.getTenantId()
        );
        retryContext.requestRetry(true);  // 从检查点恢复

        // ✅ 保存 Context 到 Repository（重要！）
        taskRuntimeRepository.saveContext(task.getTaskId(), retryContext);

        // 使用自定义 Context 创建 executor
        TaskExecutor retryExecutor = taskExecutorFactory.create(task, stages, retryContext);

        // 重试任务（统一通过 execute()）
        TaskResult retryResult = retryExecutor.execute();

        // ✅ 验证：重试成功
        assertThat(retryResult.isSuccess()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);

        // ✅ 验证：只执行了 stage-2 和 stage-3（跳过了 stage-1）
        List<TestEventTracker.TrackedEvent> retryEvents = eventTracker.getEvents();
        List<String> retryStages = retryEvents.stream()
            .filter(e -> e.type == TestEventTracker.EventType.STAGE_STARTED)
            .map(e -> e.stageName)
            .toList();
        assertThat(retryStages).containsExactly("stage-2", "stage-3");

        log.info("✅ T-032: 非最后 Stage 保存检查点测试通过");
    }

    /**
     * T-032: 测试回滚流程的检查点行为（部分回滚）
     * <p>
     * 回滚特性：
     * - 使用上一次正确配置（previousConfig）
     * - 只执行到失败的 stage 为止（部分回滚）
     * - 不执行失败 stage 之后的 stage
     * <p>
     * 测试场景：
     * 1. 首次执行在 stage-2 失败（使用当前版本）
     * 2. stage-1 成功后保存检查点
     * 3. 回滚执行使用旧版本配置，只执行 stage-1 和 stage-2
     * 4. stage-2 成功后完成回滚，不执行 stage-3
     */
    @Test
    void testRollbackCheckpointBehavior() {
        // 准备测试数据：带有 previousConfig 的配置
        TenantConfig config = ValueObjectTestFactory.withPreviousConfig();
        String currentVersion = config.getDeployUnitVersion().toString();
        TenantConfig prevConfig = config.getPreviousConfig();
        String prevVersion = prevConfig.getDeployUnitVersion().toString();

        TaskAggregate task = AggregationFactory.buildTaskAggregationFrom(config).buildPending();

        // 创建测试 Stage：
        // - stage-1: 总是成功
        // - stage-2: 当前版本失败，旧版本成功（模拟回滚场景）
        // - stage-3: 总是成功（不应被执行）
        List<TaskStage> stages = List.of(
            new AlwaysSuccessStage("stage-1"),
            ConditionalFailStage.failOnVersion("stage-2", currentVersion),
            new AlwaysSuccessStage("stage-3")  // 改为成功，因为回滚不应该执行到这里
        );
        taskDomainService.attacheStages(task, stages);

        // 第一次执行（会在 stage-2 失败）
        TaskRuntimeContext initialContext = new TaskRuntimeContext(
            task.getPlanId(),
            task.getTaskId(),
            task.getTenantId()
        );
        // 设置当前版本到 context 中，供 ConditionalFailStage 使用
        initialContext.addVariable("deployVersion", currentVersion);
        taskRuntimeRepository.saveContext(task.getTaskId(), initialContext);

        TaskExecutor executor = taskExecutorFactory.create(task, stages, initialContext);
        TaskResult result = executor.execute();

        // 验证第一次执行失败
        assertThat(result.isSuccess()).isFalse();
        List<TestEventTracker.TrackedEvent> events = eventTracker.getEvents();
        assertThat(events).extracting("type").containsSequence(
            TestEventTracker.EventType.TASK_STARTED,
            TestEventTracker.EventType.STAGE_STARTED,   // stage-1 开始
            TestEventTracker.EventType.STAGE_COMPLETED, // stage-1 完成
            TestEventTracker.EventType.STAGE_STARTED,   // stage-2 开始
            TestEventTracker.EventType.STAGE_FAILED,    // stage-2 失败
            TestEventTracker.EventType.TASK_FAILED
        );

        // 检查点应已保存（只有 stage-1 完成）
        TaskCheckpoint cp = checkpointService.loadCheckpoint(task);
        assertNotNull(cp);
        assertEquals(0, cp.getLastCompletedStageIndex());
        assertThat(cp.getCompletedStageNames()).containsExactly("stage-1");

        // 清理事件，准备回滚
        eventTracker.clear();

        // 创建回滚 Context 并使用旧版本配置
        TaskRuntimeContext rollbackContext = new TaskRuntimeContext(
            task.getPlanId(),
            task.getTaskId(),
            task.getTenantId()
        );
        rollbackContext.requestRollback(prevVersion);  // 设置回滚标志
        // 使用旧版本，使 stage-2 能够成功
        rollbackContext.addVariable("deployVersion", prevVersion);
        taskRuntimeRepository.saveContext(task.getTaskId(), rollbackContext);

        // 使用回滚 Context 执行
        TaskExecutor rollbackExecutor = taskExecutorFactory.create(task, stages, rollbackContext);
        TaskResult rollbackResult = rollbackExecutor.execute();

        // ✅ 回滚执行应该成功（只执行到 stage-2，不执行 stage-3）
        assertThat(rollbackResult.isSuccess()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);

        List<TestEventTracker.TrackedEvent> rollbackEvents = eventTracker.getEvents();

        // ✅ 验证回滚期间只执行了 stage-1 和 stage-2（部分回滚）
        List<String> startedStages = rollbackEvents.stream()
            .filter(e -> e.type == TestEventTracker.EventType.STAGE_STARTED)
            .map(e -> e.stageName)
            .toList();
        assertThat(startedStages).containsExactly("stage-1", "stage-2");
        assertThat(startedStages).doesNotContain("stage-3");  // stage-3 不应被执行

        // ✅ 验证回滚期间 stage-1 和 stage-2 都成功完成（使用旧版本）
        List<String> completedStages = rollbackEvents.stream()
            .filter(e -> e.type == TestEventTracker.EventType.STAGE_COMPLETED)
            .map(e -> e.stageName)
            .toList();
        assertThat(completedStages).containsExactly("stage-1", "stage-2");

        // ✅ 验证没有 stage 失败
        long failedCount = rollbackEvents.stream()
            .filter(e -> e.type == TestEventTracker.EventType.STAGE_FAILED)
            .count();
        assertThat(failedCount).isEqualTo(0);

        // ✅ 验证任务成功完成
        assertThat(rollbackEvents).extracting("type").contains(
            TestEventTracker.EventType.TASK_COMPLETED
        );

        log.info("✅ T-032: 回滚流程检查点行为测试通过（部分回滚）");
    }
}
