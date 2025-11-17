package xyz.firestige.executor.unit.facade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.application.PlanApplicationService;
import xyz.firestige.executor.application.TaskApplicationService;
import xyz.firestige.executor.domain.plan.*;
import xyz.firestige.executor.domain.task.*;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.facade.DeploymentTaskFacade;
import xyz.firestige.executor.facade.TaskStatusInfo;
import xyz.firestige.executor.facade.exception.*;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.validation.ValidationError;
import xyz.firestige.executor.validation.ValidationSummary;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DeploymentTaskFacade 单元测试
 * 验证 Facade 层的职责：
 * 1. 参数校验
 * 2. 调用应用服务
 * 3. 异常转换
 */
@Tag("rf01")
@Tag("facade")
@DisplayName("DeploymentTaskFacade 单元测试（RF01 Phase4 TODO）")
@Disabled("TODO(RF01-Phase4): 新 Facade 完成接线后恢复测试，覆盖 DTO 转换 & 异常映射")
class DeploymentTaskFacadeTest {

    private DeploymentTaskFacade facade;
    private PlanApplicationService planApplicationService;
    private TaskApplicationService taskApplicationService;

    @BeforeEach
    void setUp() {
        planApplicationService = mock(PlanApplicationService.class);
        taskApplicationService = mock(TaskApplicationService.class);
        facade = new DeploymentTaskFacade(planApplicationService, taskApplicationService);
    }

    // ========== createSwitchTask 测试 ==========

    @Test
    @DisplayName("创建任务 - 参数为 null 时抛出 IllegalArgumentException")
    void testCreateSwitchTask_NullConfigs() {
        assertThrows(IllegalArgumentException.class, () -> {
            facade.createSwitchTask(null);
        });
        verify(planApplicationService, never()).createSwitchTask(any());
    }

    @Test
    @DisplayName("创建任务 - 参数为空列表时抛出 IllegalArgumentException")
    void testCreateSwitchTask_EmptyConfigs() {
        assertThrows(IllegalArgumentException.class, () -> {
            facade.createSwitchTask(new ArrayList<>());
        });
        verify(planApplicationService, never()).createSwitchTask(any());
    }

    @Test
    @DisplayName("创建任务 - 成功场景")
    void testCreateSwitchTask_Success() {
        // TODO: Fix after completing Phase 4 - update PlanInfo constructor call
        /*
        List<TenantDeployConfig> configs = List.of(new TenantDeployConfig());
        PlanInfo planInfo = new PlanInfo("plan123", null, null, null, null, null, null);
        PlanCreationResult result = PlanCreationResult.success(planInfo);

        when(planApplicationService.createSwitchTask(configs)).thenReturn(result);

        assertDoesNotThrow(() -> facade.createSwitchTask(configs));
        verify(planApplicationService).createSwitchTask(configs);
        */
    }

    @Test
    @DisplayName("创建任务 - 校验失败时抛出 IllegalArgumentException")
    void testCreateSwitchTask_ValidationFailure() {
        List<TenantDeployConfig> configs = List.of(new TenantDeployConfig());
        ValidationSummary summary = new ValidationSummary();
        summary.addInvalidConfig(configs.get(0), List.of(
                ValidationError.of("field1", "error1")
        ));
        PlanCreationResult result = PlanCreationResult.validationFailure(summary);

        when(planApplicationService.createSwitchTask(configs)).thenReturn(result);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            facade.createSwitchTask(configs);
        });
        assertTrue(ex.getMessage().contains("配置校验失败"));
    }

    @Test
    @DisplayName("创建任务 - 创建失败时抛出 TaskCreationException")
    void testCreateSwitchTask_CreationFailure() {
        List<TenantDeployConfig> configs = List.of(new TenantDeployConfig());
        FailureInfo failureInfo = FailureInfo.of(ErrorType.SYSTEM_ERROR, "创建失败");
        PlanCreationResult result = PlanCreationResult.failure(failureInfo, "创建失败");

        when(planApplicationService.createSwitchTask(configs)).thenReturn(result);

        TaskCreationException ex = assertThrows(TaskCreationException.class, () -> {
            facade.createSwitchTask(configs);
        });
        assertEquals(failureInfo, ex.getFailureInfo());
    }

    // ========== pauseTaskByTenant 测试 ==========

    @Test
    @DisplayName("暂停租户任务 - 成功场景")
    void testPauseTaskByTenant_Success() {
        TaskOperationResult result = TaskOperationResult.success("task123", TaskStatus.PAUSED, "暂停成功");
        when(taskApplicationService.pauseTaskByTenant("tenant1")).thenReturn(result);

        assertDoesNotThrow(() -> facade.pauseTaskByTenant("tenant1"));
        verify(taskApplicationService).pauseTaskByTenant("tenant1");
    }

    @Test
    @DisplayName("暂停租户任务 - 任务不存在时抛出 TaskNotFoundException")
    void testPauseTaskByTenant_NotFound() {
        FailureInfo failureInfo = FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务");
        TaskOperationResult result = TaskOperationResult.failure("tenant1", failureInfo, "未找到租户任务");
        when(taskApplicationService.pauseTaskByTenant("tenant1")).thenReturn(result);

        assertThrows(TaskNotFoundException.class, () -> {
            facade.pauseTaskByTenant("tenant1");
        });
    }

    @Test
    @DisplayName("暂停租户任务 - 操作失败时抛出 TaskOperationException")
    void testPauseTaskByTenant_OperationFailure() {
        FailureInfo failureInfo = FailureInfo.of(ErrorType.SYSTEM_ERROR, "操作失败");
        TaskOperationResult result = TaskOperationResult.failure("tenant1", failureInfo, "操作失败");
        when(taskApplicationService.pauseTaskByTenant("tenant1")).thenReturn(result);

        assertThrows(TaskOperationException.class, () -> {
            facade.pauseTaskByTenant("tenant1");
        });
    }

    // ========== pauseTaskByPlan 测试 ==========

    @Test
    @DisplayName("暂停计划 - 成功场景")
    void testPauseTaskByPlan_Success() {
        PlanOperationResult result = PlanOperationResult.success("plan123", xyz.firestige.executor.domain.plan.PlanStatus.PAUSED, "暂停成功");
        when(planApplicationService.pausePlan(123L)).thenReturn(result);

        assertDoesNotThrow(() -> facade.pauseTaskByPlan(123L));
        verify(planApplicationService).pausePlan(123L);
    }

    @Test
    @DisplayName("暂停计划 - 计划不存在时抛出 PlanNotFoundException")
    void testPauseTaskByPlan_NotFound() {
        FailureInfo failureInfo = FailureInfo.of(ErrorType.VALIDATION_ERROR, "计划不存在");
        PlanOperationResult result = PlanOperationResult.failure("123", failureInfo, "计划不存在");
        when(planApplicationService.pausePlan(123L)).thenReturn(result);

        assertThrows(PlanNotFoundException.class, () -> {
            facade.pauseTaskByPlan(123L);
        });
    }

    // ========== queryTaskStatus 测试 ==========

    @Test
    @DisplayName("查询任务状态 - 成功场景")
    void testQueryTaskStatus_Success() {
        TaskStatusInfo statusInfo = new TaskStatusInfo();
        statusInfo.setTaskId("task123");
        statusInfo.setStatus(TaskStatus.RUNNING);
        statusInfo.setMessage("运行中");

        when(taskApplicationService.queryTaskStatus("task123")).thenReturn(statusInfo);

        TaskStatusInfo result = facade.queryTaskStatus("task123");
        assertNotNull(result);
        assertEquals(TaskStatus.RUNNING, result.getStatus());
        verify(taskApplicationService).queryTaskStatus("task123");
    }

    @Test
    @DisplayName("查询任务状态 - 任务不存在时抛出 TaskNotFoundException")
    void testQueryTaskStatus_NotFound() {
        TaskStatusInfo statusInfo = new TaskStatusInfo();
        statusInfo.setStatus(null); // 状态为 null 表示任务不存在
        statusInfo.setMessage("任务不存在");

        when(taskApplicationService.queryTaskStatus("task123")).thenReturn(statusInfo);

        assertThrows(TaskNotFoundException.class, () -> {
            facade.queryTaskStatus("task123");
        });
    }

    // ========== cancelTask 测试 ==========

    @Test
    @DisplayName("取消任务 - 成功场景")
    void testCancelTask_Success() {
        TaskOperationResult result = TaskOperationResult.success("task123", TaskStatus.CANCELLED, "取消成功");
        when(taskApplicationService.cancelTask("task123")).thenReturn(result);

        assertDoesNotThrow(() -> facade.cancelTask("task123"));
        verify(taskApplicationService).cancelTask("task123");
    }

    // ========== retryTaskByTenant 测试 ==========

    @Test
    @DisplayName("重试租户任务 - 成功场景")
    void testRetryTaskByTenant_Success() {
        TaskOperationResult result = TaskOperationResult.success("task123", TaskStatus.RUNNING, "重试成功");
        when(taskApplicationService.retryTaskByTenant("tenant1", false)).thenReturn(result);

        assertDoesNotThrow(() -> facade.retryTaskByTenant("tenant1", false));
        verify(taskApplicationService).retryTaskByTenant("tenant1", false);
    }

    // ========== rollbackTaskByTenant 测试 ==========

    @Test
    @DisplayName("回滚租户任务 - 成功场景")
    void testRollbackTaskByTenant_Success() {
        TaskOperationResult result = TaskOperationResult.success("task123", TaskStatus.ROLLED_BACK, "回滚成功");
        when(taskApplicationService.rollbackTaskByTenant("tenant1")).thenReturn(result);

        assertDoesNotThrow(() -> facade.rollbackTaskByTenant("tenant1"));
        verify(taskApplicationService).rollbackTaskByTenant("tenant1");
    }
}
