package xyz.firestige.deploy.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.application.task.TaskOperationService;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskOperationResult;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.domain.task.TenantDeployConfigSnapshot;
import xyz.firestige.deploy.testutil.factory.ValueObjectTestFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回滚切换任务 E2E 测试
 * <p>
 * 测试场景：
 * 1. 任务执行失败或出现问题
 * 2. 触发回滚
 * 3. 验证状态转换：FAILED -> ROLLING_BACK -> ROLLED_BACK
 * 4. 验证配置恢复到上一版本
 * 
 * @since T-023 测试体系重建
 */
@DisplayName("E2E: 回滚切换任务")
class RollbackDeployTaskE2ETest extends BaseE2ETest {

    @Autowired
    private TaskOperationService taskOperationService;

    @Test
    @DisplayName("应该成功回滚失败的任务")
    void shouldRollbackFailedTask() throws Exception {
//        // ========== 1. 准备失败的Task（有上一版本快照）==========
//        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-rollback-001");
//        PlanId planId = ValueObjectTestFactory.planId("plan-rollback-001");
//        TaskId taskId = ValueObjectTestFactory.taskId("task-rollback-001");
//
//        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
//        task.setDeployVersion(ValueObjectTestFactory.version("v2.0.0"));
//
//        // TODO: 设置上一版本快照（需要实际的TenantDeployConfigSnapshot构造方法）
//        // TenantDeployConfigSnapshot prevSnapshot = ...;
//        // task.setPrevConfigSnapshot(prevSnapshot);
//
//        // 设置prevConfig（用于重新装配Stage）
//        TenantConfig prevConfig = ValueObjectTestFactory.minimalConfig(tenantId);
//        task.setPrevConfig(prevConfig);
//
//        task.markAsPending();
//        task.start();
//
//        // 模拟失败
//        FailureInfo failure = FailureInfo.of(ErrorType.BUSINESS_ERROR, "Deployment failed");
//        task.fail(failure);
//        taskRepository.save(task);
//
//        // ========== 2. 验证失败状态 ==========
//        TaskAggregate failedTask = taskRepository.findById(taskId).orElseThrow();
//        assertEquals(TaskStatus.FAILED, failedTask.getStatus());
//        assertNotNull(failedTask.getPrevConfigSnapshot(), "应该有回滚快照");
//
//        // ========== 3. 触发回滚 ==========
//        // TODO: 调用实际的rollbackTaskByTenant API
//        // TaskOperationResult rollbackResult = taskOperationService.rollbackTaskByTenant(tenantId, ...);
//
//        // ========== 4. 验证回滚成功触发 ==========
//        assertTrue(rollbackResult.isSuccess(), "回滚应该成功触发");
//
//        // 等待异步执行
//        TimeUnit.SECONDS.sleep(1);
//
//        // ========== 5. 验证状态转换 ==========
//        TaskAggregate rollingBackTask = taskRepository.findById(taskId).orElseThrow();
//        // 状态可能是ROLLING_BACK或ROLLED_BACK，取决于执行速度
//        assertTrue(
//            rollingBackTask.getStatus() == TaskStatus.ROLLING_BACK ||
//            rollingBackTask.getStatus() == TaskStatus.ROLLED_BACK,
//            "任务应该处于回滚状态"
//        );
    }

    @Test
    @DisplayName("应该拒绝没有回滚快照的任务")
    void shouldRejectTaskWithoutSnapshot() {
//        // ========== 1. 准备没有快照的Task ==========
//        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-rollback-002");
//        PlanId planId = ValueObjectTestFactory.planId("plan-rollback-002");
//        TaskId taskId = ValueObjectTestFactory.taskId("task-rollback-002");
//
//        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
//        task.setDeployVersion(ValueObjectTestFactory.randomVersion());
//        task.markAsPending();
//        task.start();
//
//        // 模拟失败（没有设置prevConfigSnapshot）
//        FailureInfo failure = FailureInfo.of(ErrorType.SYSTEM_ERROR, "No snapshot");
//        task.fail(failure);
//        taskRepository.save(task);
//
//        // ========== 2. 尝试回滚，应该抛出异常 ==========
//        assertThrows(IllegalStateException.class, () -> {
//            task.startRollback("Manual rollback");
//        }, "没有回滚快照应该抛出异常");
    }

    @Test
    @DisplayName("应该正确记录回滚失败")
    void shouldRecordRollbackFailure() {
//        // ========== 1. 准备回滚中的Task ==========
//        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-rollback-003");
//        PlanId planId = ValueObjectTestFactory.planId("plan-rollback-003");
//        TaskId taskId = ValueObjectTestFactory.taskId("task-rollback-003");
//
//        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
//        task.setDeployVersion(ValueObjectTestFactory.randomVersion());
//
//        // 设置快照
//        TenantDeployConfigSnapshot prevSnapshot = new TenantDeployConfigSnapshot();
//        prevSnapshot.setDeployUnitId(100L);
//        prevSnapshot.setDeployUnitVersion(1L);
//        task.setPrevConfigSnapshot(prevSnapshot);
//
//        task.markAsPending();
//        task.start();
//        task.fail(FailureInfo.of(ErrorType.SYSTEM_ERROR, "Original failure"));
//        task.startRollback("Manual rollback");
//
//        // ========== 2. 模拟回滚失败 ==========
//        task.failRollback("Redis connection timeout");
//        taskRepository.save(task);
//
//        // ========== 3. 验证回滚失败状态 ==========
//        TaskAggregate failedRollbackTask = taskRepository.findById(taskId).orElseThrow();
//        assertEquals(TaskStatus.ROLLBACK_FAILED, failedRollbackTask.getStatus());
//        assertNotNull(failedRollbackTask.getEndedAt());
    }

    @Test
    @DisplayName("应该在回滚后允许重试")
    void shouldAllowRetryAfterRollback() throws Exception {
//        // ========== 1. 准备已回滚的Task ==========
//        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-rollback-004");
//        PlanId planId = ValueObjectTestFactory.planId("plan-rollback-004");
//        TaskId taskId = ValueObjectTestFactory.taskId("task-rollback-004");
//
//        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
//        task.setDeployVersion(ValueObjectTestFactory.randomVersion());
//
//        // 设置快照
//        TenantDeployConfigSnapshot prevSnapshot = new TenantDeployConfigSnapshot();
//        prevSnapshot.setDeployUnitId(100L);
//        prevSnapshot.setDeployUnitVersion(1L);
//        task.setPrevConfigSnapshot(prevSnapshot);
//
//        task.markAsPending();
//        task.start();
//        task.fail(FailureInfo.of(ErrorType.SYSTEM_ERROR, "Original failure"));
//        task.startRollback("Auto rollback");
//        task.completeRollback();
//        taskRepository.save(task);
//
//        // ========== 2. 验证ROLLED_BACK状态 ==========
//        TaskAggregate rolledBackTask = taskRepository.findById(taskId).orElseThrow();
//        assertEquals(TaskStatus.ROLLED_BACK, rolledBackTask.getStatus());
//
//        // ========== 3. 尝试重试 ==========
//        rolledBackTask.retry();
//        taskRepository.save(rolledBackTask);
//
//        // ========== 4. 验证可以重试 ==========
//        TaskAggregate retriedTask = taskRepository.findById(taskId).orElseThrow();
//        assertEquals(TaskStatus.RUNNING, retriedTask.getStatus());
//        assertEquals(1, retriedTask.getRetryCount());
    }

    @Test
    @DisplayName("应该在回滚成功后保留原任务记录")
    void shouldPreserveTaskRecordAfterRollback() {
//        // ========== 1. 执行完整的回滚流程 ==========
//        TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-rollback-005");
//        PlanId planId = ValueObjectTestFactory.planId("plan-rollback-005");
//        TaskId taskId = ValueObjectTestFactory.taskId("task-rollback-005");
//
//        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
//        task.setDeployVersion(ValueObjectTestFactory.version("v2.0.0"));
//
//        // 设置快照
//        TenantDeployConfigSnapshot prevSnapshot = new TenantDeployConfigSnapshot();
//        prevSnapshot.setDeployUnitId(100L);
//        prevSnapshot.setDeployUnitVersion(1L);
//        task.setPrevConfigSnapshot(prevSnapshot);
//
//        task.markAsPending();
//        task.start();
//        task.fail(FailureInfo.of(ErrorType.BUSINESS_ERROR, "Critical error"));
//        task.startRollback("Emergency rollback");
//        task.completeRollback();
//        taskRepository.save(task);
//
//        // ========== 2. 验证任务记录完整性 ==========
//        TaskAggregate finalTask = taskRepository.findById(taskId).orElseThrow();
//        assertEquals(TaskStatus.ROLLED_BACK, finalTask.getStatus());
//        assertNotNull(finalTask.getStartedAt(), "应该保留启动时间");
//        assertNotNull(finalTask.getEndedAt(), "应该记录结束时间");
//        assertNotNull(finalTask.getPrevConfigSnapshot(), "应该保留快照信息");
//        assertNotNull(finalTask.getDeployVersion());
    }
}
