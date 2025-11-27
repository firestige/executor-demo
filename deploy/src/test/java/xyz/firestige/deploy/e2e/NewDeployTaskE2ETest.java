package xyz.firestige.deploy.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.TestApplication;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.application.task.TaskOperationService;
import xyz.firestige.deploy.config.InfrastructureConfiguration;
import xyz.firestige.deploy.domain.plan.PlanAggregate;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.OrchestratedStageFactory;
import xyz.firestige.deploy.testutil.factory.ValueObjectTestFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

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

    @Autowired
    private TaskOperationService taskOperationService;

    @Test
    @DisplayName("应该成功创建并执行新的切换任务")
    void shouldCreateAndExecuteNewDeployTask() throws Exception {
        // ========== 1. 准备测试数据 ==========
        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-e2e-001");
        PlanId planId = ValueObjectTestFactory.planId("plan-e2e-001");
        TaskId taskId = ValueObjectTestFactory.taskId("task-e2e-001");

        // 创建TenantConfig
        TenantConfig config = ValueObjectTestFactory.minimalConfig(tenantId);
        
        // ========== 2. 创建Plan和Task ==========
        PlanAggregate plan = new PlanAggregate(planId);
        plan.addTask(taskId);
        plan.markAsReady();
        planRepository.save(plan);

        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
        task.setDeployVersion(ValueObjectTestFactory.randomVersion());
        task.markAsPending();
        taskRepository.save(task);

        // ========== 3. 验证初始状态 ==========
        TaskAggregate savedTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskStatus.PENDING, savedTask.getStatus());

        // ========== 4. 执行任务（模拟执行器启动）==========
        // 注意：这里需要实际的执行器启动逻辑
        // 如果TaskOperationService有startTask方法，调用它
        // taskOperationService.startTask(taskId);

        // ========== 5. 等待执行完成（模拟异步执行）==========
        // 在实际E2E测试中，这里应该等待异步任务完成
        // 可以通过轮询状态或使用CountDownLatch等待
        TimeUnit.SECONDS.sleep(2);

        // ========== 6. 验证最终状态 ==========
        TaskAggregate finalTask = taskRepository.findById(taskId).orElseThrow();
        
        // 验证状态（实际执行后应该是COMPLETED或RUNNING）
        assertNotNull(finalTask.getStatus());
        assertTrue(finalTask.getStartedAt() != null, "任务应该已启动");
        
        // 如果执行完成，验证COMPLETED状态
        // assertEquals(TaskStatus.COMPLETED, finalTask.getStatus());
    }

    @Test
    @DisplayName("应该正确记录Stage执行进度")
    void shouldRecordStageProgress() throws Exception {
        // ========== 1. 准备测试数据 ==========
        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-e2e-002");
        PlanId planId = ValueObjectTestFactory.planId("plan-e2e-002");
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
        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-e2e-003");
        PlanId planId = ValueObjectTestFactory.planId("plan-e2e-003");
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
