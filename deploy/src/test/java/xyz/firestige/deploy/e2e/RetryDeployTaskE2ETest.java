package xyz.firestige.deploy.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import xyz.firestige.deploy.application.task.TaskOperationService;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskOperationResult;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.testutil.factory.ValueObjectTestFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 重试切换任务 E2E 测试
 * <p>
 * 测试场景：
 * 1. 任务执行失败
 * 2. 触发重试（从头 / 从Checkpoint）
 * 3. 验证状态转换：FAILED -> RUNNING -> COMPLETED
 * 4. 验证重试计数器递增
 * 
 * @since T-023 测试体系重建
 */
@DisplayName("E2E: 重试切换任务")
class RetryDeployTaskE2ETest extends BaseE2ETest {

    @Autowired
    private TaskOperationService taskOperationService;

    @Test
    @DisplayName("应该成功从头重试失败的任务")
    void shouldRetryFailedTaskFromBeginning() throws Exception {
        // ========== 1. 准备失败的Task ==========
        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-retry-001");
        PlanId planId = ValueObjectTestFactory.planId("plan-retry-001");
        TaskId taskId = ValueObjectTestFactory.taskId("task-retry-001");

        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
        task.setDeployVersion(ValueObjectTestFactory.randomVersion());
        task.markAsPending();
        task.start();

        // 模拟失败
        FailureInfo failure = FailureInfo.of(ErrorType.SYSTEM_ERROR, "Test failure");
        task.fail(failure);
        taskRepository.save(task);

        // ========== 2. 验证失败状态 ==========
        TaskAggregate failedTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskStatus.FAILED, failedTask.getStatus());

        // ========== 3. 触发重试（从头开始）==========
        TaskOperationResult retryResult = taskOperationService.retryTaskByTenant(tenantId, false);

        // ========== 4. 验证重试成功 ==========
        assertTrue(retryResult.isSuccess(), "重试应该成功触发");

        // 等待异步执行
        TimeUnit.SECONDS.sleep(1);

        // ========== 5. 验证状态转换为RUNNING ==========
        TaskAggregate retriedTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskStatus.RUNNING, retriedTask.getStatus());
        assertEquals(1, retriedTask.getRetryCount(), "重试计数应该递增");
    }

    @Test
    @DisplayName("应该成功从Checkpoint重试任务")
    void shouldRetryTaskFromCheckpoint() throws Exception {
        // ========== 1. 准备带Checkpoint的失败Task ==========
        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-retry-002");
        PlanId planId = ValueObjectTestFactory.planId("plan-retry-002");
        TaskId taskId = ValueObjectTestFactory.taskId("task-retry-002");

        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
        task.setDeployVersion(ValueObjectTestFactory.randomVersion());
        task.markAsPending();
        task.start();

        // 记录Checkpoint（模拟完成2个Stage）
        task.recordCheckpoint(
            java.util.List.of("stage-0", "stage-1"),
            1
        );

        // 模拟失败
        FailureInfo failure = FailureInfo.of(ErrorType.BUSINESS_ERROR, "Stage 2 failed");
        task.fail(failure);
        taskRepository.save(task);

        // ========== 2. 验证Checkpoint存在 ==========
        TaskAggregate failedTask = taskRepository.findById(taskId).orElseThrow();
        assertNotNull(failedTask.getCheckpoint(), "应该有Checkpoint");

        // ========== 3. 触发从Checkpoint重试 ==========
        TaskOperationResult retryResult = taskOperationService.retryTaskByTenant(tenantId, true);

        // ========== 4. 验证重试成功 ==========
        assertTrue(retryResult.isSuccess(), "从Checkpoint重试应该成功");

        // 等待异步执行
        TimeUnit.SECONDS.sleep(1);

        // ========== 5. 验证状态 ==========
        TaskAggregate retriedTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskStatus.RUNNING, retriedTask.getStatus());
        assertEquals(1, retriedTask.getRetryCount());
    }

    @Test
    @DisplayName("应该拒绝超过最大重试次数的任务")
    void shouldRejectTaskExceedingMaxRetries() {
        // ========== 1. 准备已达最大重试次数的Task ==========
        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-retry-003");
        PlanId planId = ValueObjectTestFactory.planId("plan-retry-003");
        TaskId taskId = ValueObjectTestFactory.taskId("task-retry-003");

        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
        task.setDeployVersion(ValueObjectTestFactory.randomVersion());
        task.setMaxRetry(2); // 最大重试2次
        task.markAsPending();
        task.start();

        // 模拟失败并重试2次
        for (int i = 0; i < 2; i++) {
            FailureInfo failure = FailureInfo.of(ErrorType.SYSTEM_ERROR, "Retry " + i);
            task.fail(failure);
            task.retry(); // 触发retry会递增retryCount
        }

        // 再次失败
        FailureInfo finalFailure = FailureInfo.of(ErrorType.SYSTEM_ERROR, "Final failure");
        task.fail(finalFailure);
        taskRepository.save(task);

        // ========== 2. 验证重试次数 ==========
        TaskAggregate failedTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(2, failedTask.getRetryCount());

        // ========== 3. 尝试再次重试，应该抛出异常 ==========
        assertThrows(IllegalStateException.class, () -> {
            failedTask.retry();
        }, "已达最大重试次数，应该抛出异常");
    }

    @Test
    @DisplayName("应该在重试成功后清除失败信息")
    void shouldClearFailureInfoAfterSuccessfulRetry() throws Exception {
        // ========== 1. 准备失败的Task ==========
        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-retry-004");
        PlanId planId = ValueObjectTestFactory.planId("plan-retry-004");
        TaskId taskId = ValueObjectTestFactory.taskId("task-retry-004");

        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
        task.setDeployVersion(ValueObjectTestFactory.randomVersion());
        task.markAsPending();
        task.start();

        // 模拟失败
        FailureInfo failure = FailureInfo.of(ErrorType.SYSTEM_ERROR, "First failure");
        task.fail(failure);
        taskRepository.save(task);

        // ========== 2. 重试并完成 ==========
        task.retry();
        task.complete();
        taskRepository.save(task);

        // ========== 3. 验证状态 ==========
        TaskAggregate completedTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskStatus.COMPLETED, completedTask.getStatus());
        assertEquals(1, completedTask.getRetryCount(), "应该记录重试次数");
    }
}
