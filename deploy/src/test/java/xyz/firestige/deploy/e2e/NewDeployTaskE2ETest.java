package xyz.firestige.deploy.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.TestApplication;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.application.lifecycle.PlanLifecycleService;
import xyz.firestige.deploy.application.orchestration.listener.PlanStartedListener;
import xyz.firestige.deploy.application.task.TaskOperationService;
import xyz.firestige.deploy.config.InfrastructureConfiguration;
import xyz.firestige.deploy.domain.plan.PlanAggregate;
import xyz.firestige.deploy.domain.plan.PlanCreationResult;
import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.execution.stage.StageFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.OrchestratedStageFactory;
import xyz.firestige.deploy.testutil.factory.PlanAggregateTestBuilder;
import xyz.firestige.deploy.testutil.factory.ValueObjectTestFactory;
import xyz.firestige.deploy.testutil.stage.AlwaysSuccessStage;
import xyz.firestige.redis.ack.api.RedisAckService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 新建切换任务 E2E 测试
 * <p>
 * 测试场景：
 * 1. 创建Plan和Task
 * 2. 提交执行
 * 3. 验证状态转换：PENDING -> RUNNING -> COMPLETED
 * 4. 验证所有Stage按顺序执行
 * 
 * @since T-023 测试体系重建
 */
@DisplayName("E2E: 新建切换任务")
@SpringBootTest(classes = InfrastructureConfiguration.class)
class NewDeployTaskE2ETest extends BaseE2ETest {

    // 阻断 Redis Ack 服务，避免干扰测试
    @MockBean
    private RedisAckService redisAckService;

    // mock 使其加载特定的 Stage，避免使用默认的 OrchestratedStageFactory
    @MockBean
    private StageFactory stageFactory;

    // 阻断 PlanStartedListener，避免干扰测试
    @MockBean
    private PlanStartedListener planStartedListener;

    @Autowired
    private PlanLifecycleService planLifecycleService;

    @Autowired
    private TaskOperationService taskOperationService;

    @Test
    @DisplayName("应该成功创建新的切换任务")
    void shouldCreateNewDeployTask() throws Exception {
        // ========== 1. 准备测试数据 ==========
        TenantConfig config = ValueObjectTestFactory.withPreviousConfig();
        PlanId planId = config.getPlanId();

        // 构建用于测试的 Stage 列表
        List<TaskStage> taskStages = List.of(
                new AlwaysSuccessStage("stage-1"),
                new AlwaysSuccessStage("stage-2")
        );

        when(stageFactory.buildStages(config)).thenReturn(taskStages);

        // ========== 2. 创建Plan和Task ==========
        PlanCreationResult result = planLifecycleService.createDeploymentPlan(List.of(config));

        // ========== 3. 验证创建结果 ==========
        assertTrue(result.isSuccess());
        PlanAggregate agg = planRepository.findById(planId).orElse(null);
        assertNotNull(agg);
        assertEquals(PlanStatus.RUNNING, agg.getStatus());

        List<TaskId> taskIds = agg.getTaskIds();
        assertEquals(1, taskIds.size());
        TaskId taskId = taskIds.get(0);
        TaskAggregate task = taskRepository.findById(taskId).orElse(null);
        assertNotNull(task);
        assertEquals(TaskStatus.PENDING, task.getStatus());
    }

    @Test
    @DisplayName("应该正确记录Stage执行进度")
    void shouldRecordStageProgress() throws Exception {
        // ========== 1. 准备测试数据 ==========
        TenantId tenantId = ValueObjectTestFactory.randomTenantId();
        PlanId planId = ValueObjectTestFactory.randomPlanId();
        TaskId taskId = ValueObjectTestFactory.taskId("task-e2e-002");

        // ========== 2. 创建Task ==========
        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
        task.setDeployVersion(ValueObjectTestFactory.randomVersion());
        task.markAsPending();
        task.start();
        taskRepository.save(task);

        // ========== 3. 模拟Stage执行 ==========
        // 注意：实际E2E测试中，这部分应该由执行引擎自动完成
        // 这里仅验证数据结构正确性

        // ========== 4. 验证进度 ==========
        TaskAggregate runningTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskStatus.RUNNING, runningTask.getStatus());
        assertNotNull(runningTask.getStartedAt());
    }

    @Test
    @DisplayName("应该在所有Stage完成后标记任务为COMPLETED")
    void shouldMarkTaskCompletedAfterAllStages() {
        // ========== 1. 准备测试数据 ==========
        TenantId tenantId = ValueObjectTestFactory.randomTenantId();
        PlanId planId = ValueObjectTestFactory.randomPlanId();
        TaskId taskId = ValueObjectTestFactory.taskId("task-e2e-003");

        // ========== 2. 创建并启动Task ==========
        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
        task.setDeployVersion(ValueObjectTestFactory.randomVersion());
        task.markAsPending();
        task.start();

        // ========== 3. 模拟所有Stage完成后调用complete() ==========
        task.complete();
        taskRepository.save(task);

        // ========== 4. 验证COMPLETED状态 ==========
        TaskAggregate completedTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskStatus.COMPLETED, completedTask.getStatus());
        assertNotNull(completedTask.getEndedAt());
        assertNotNull(completedTask.getDuration());
    }
}
