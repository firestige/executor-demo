package xyz.firestige.executor.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.application.PlanApplicationService;
import xyz.firestige.executor.application.TaskApplicationService;
import xyz.firestige.executor.application.dto.*;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.facade.exception.*;
import xyz.firestige.executor.validation.ValidationError;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 部署任务 Facade（RF01 重构版）
 *
 * 职责：
 * 1. DTO 转换：外部 DTO (TenantDeployConfig) → 内部 DTO (暂时仍使用 TenantDeployConfig，待后续替换)
 * 2. 参数校验（快速失败）
 * 3. 调用应用服务
 * 4. 异常转换：应用层 Result → Facade 异常
 *
 * 设计说明：
 * - 不定义接口，直接使用具体类（符合 YAGNI 原则）
 * - 返回 void（查询操作除外），通过异常机制处理错误
 * - 保护应用层接口稳定，外部 DTO 变化不影响应用层
 */
@Component
public class DeploymentTaskFacade {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentTaskFacade.class);

    private final PlanApplicationService planApplicationService;
    private final TaskApplicationService taskApplicationService;

    public DeploymentTaskFacade(
            PlanApplicationService planApplicationService,
            TaskApplicationService taskApplicationService) {
        this.planApplicationService = planApplicationService;
        this.taskApplicationService = taskApplicationService;
    }

    /**
     * 创建切换任务
     * @param configs 租户部署配置列表（外部 DTO）
     * @throws IllegalArgumentException 参数校验失败
     * @throws TaskCreationException 任务创建失败
     */
    public void createSwitchTask(List<TenantDeployConfig> configs) {
        logger.info("[Facade] 创建切换任务，配置数量: {}", configs != null ? configs.size() : 0);

        // 1. 参数校验（快速失败）
        if (configs == null || configs.isEmpty()) {
            throw new IllegalArgumentException("配置列表不能为空");
        }

        // 2. 调用应用服务（当前仍使用外部 DTO，待 Phase 6 引入 TenantConfig 后再替换）
        PlanCreationResult result = planApplicationService.createSwitchTask(configs);

        // 3. 处理结果 - 失败时抛出异常
        if (!result.isSuccess()) {
            // 3.1 校验失败 - 包装详细错误信息
            if (result.getValidationSummary() != null && result.getValidationSummary().hasErrors()) {
                String errorDetail = formatValidationErrors(result.getValidationSummary());
                throw new IllegalArgumentException("配置校验失败: " + errorDetail);
            }

            // 3.2 其他失败 - 抛出业务异常
            FailureInfo failureInfo = result.getFailureInfo();
            throw new TaskCreationException(
                failureInfo != null ? failureInfo.getErrorMessage() : "任务创建失败",
                failureInfo
            );
        }

        // 成功：记录日志
        PlanInfo planInfo = result.getPlanInfo();
        logger.info("[Facade] Plan 创建成功，planId: {}, tasks: {}",
                    planInfo.getPlanId(), planInfo.getTasks().size());
    }

    /**
     * 根据租户 ID 暂停任务
     */
    public void pauseTaskByTenant(String tenantId) {
        logger.info("[Facade] 暂停租户任务: {}", tenantId);

        TaskOperationResult result = taskApplicationService.pauseTaskByTenant(tenantId);
        handleTaskOperationResult(result, "暂停任务");

        logger.info("[Facade] 租户任务暂停成功: {}", tenantId);
    }

    /**
     * 根据计划 ID 暂停任务
     */
    public void pauseTaskByPlan(Long planId) {
        logger.info("[Facade] 暂停计划: {}", planId);

        PlanOperationResult result = planApplicationService.pausePlan(planId);
        handlePlanOperationResult(result, "暂停计划");

        logger.info("[Facade] 计划暂停成功: {}", planId);
    }

    /**
     * 根据租户 ID 恢复任务
     */
    public void resumeTaskByTenant(String tenantId) {
        logger.info("[Facade] 恢复租户任务: {}", tenantId);

        TaskOperationResult result = taskApplicationService.resumeTaskByTenant(tenantId);
        handleTaskOperationResult(result, "恢复任务");

        logger.info("[Facade] 租户任务恢复成功: {}", tenantId);
    }

    /**
     * 根据计划 ID 恢复任务
     */
    public void resumeTaskByPlan(Long planId) {
        logger.info("[Facade] 恢复计划: {}", planId);

        PlanOperationResult result = planApplicationService.resumePlan(planId);
        handlePlanOperationResult(result, "恢复计划");

        logger.info("[Facade] 计划恢复成功: {}", planId);
    }

    /**
     * 根据租户 ID 回滚任务
     */
    public void rollbackTaskByTenant(String tenantId) {
        logger.info("[Facade] 回滚租户任务: {}", tenantId);

        TaskOperationResult result = taskApplicationService.rollbackTaskByTenant(tenantId);
        handleTaskOperationResult(result, "回滚任务");

        logger.info("[Facade] 租户任务回滚成功: {}", tenantId);
    }

    /**
     * 根据计划 ID 回滚任务
     */
    public void rollbackTaskByPlan(Long planId) {
        logger.info("[Facade] 回滚计划: {}", planId);

        PlanOperationResult result = planApplicationService.rollbackPlan(planId);
        handlePlanOperationResult(result, "回滚计划");

        logger.info("[Facade] 计划回滚成功: {}", planId);
    }

    /**
     * 根据租户 ID 重试任务
     */
    public void retryTaskByTenant(String tenantId, boolean fromCheckpoint) {
        logger.info("[Facade] 重试租户任务: {}, fromCheckpoint: {}", tenantId, fromCheckpoint);

        TaskOperationResult result = taskApplicationService.retryTaskByTenant(tenantId, fromCheckpoint);
        handleTaskOperationResult(result, "重试任务");

        logger.info("[Facade] 租户任务重试成功: {}", tenantId);
    }

    /**
     * 根据计划 ID 重试任务
     */
    public void retryTaskByPlan(Long planId, boolean fromCheckpoint) {
        logger.info("[Facade] 重试计划: {}, fromCheckpoint: {}", planId, fromCheckpoint);

        PlanOperationResult result = planApplicationService.retryPlan(planId, fromCheckpoint);
        handlePlanOperationResult(result, "重试计划");

        logger.info("[Facade] 计划重试成功: {}", planId);
    }

    /**
     * 查询任务状态
     * @return 任务状态信息（查询操作保留返回值）
     * @throws TaskNotFoundException 任务不存在
     */
    public xyz.firestige.executor.facade.TaskStatusInfo queryTaskStatus(String executionUnitId) {
        logger.debug("[Facade] 查询任务状态: {}", executionUnitId);

        xyz.firestige.executor.facade.TaskStatusInfo result = taskApplicationService.queryTaskStatus(executionUnitId);

        // 查询失败时抛出异常
        if (result.getStatus() == null) {
            throw new TaskNotFoundException("任务不存在: " + executionUnitId);
        }

        return result;
    }

    /**
     * 根据租户 ID 查询任务状态
     */
    public xyz.firestige.executor.facade.TaskStatusInfo queryTaskStatusByTenant(String tenantId) {
        logger.debug("[Facade] 查询租户任务状态: {}", tenantId);

        xyz.firestige.executor.facade.TaskStatusInfo result = taskApplicationService.queryTaskStatusByTenant(tenantId);

        if (result.getStatus() == null) {
            throw new TaskNotFoundException("租户任务不存在: " + tenantId);
        }

        return result;
    }

    /**
     * 取消任务
     */
    public void cancelTask(String executionUnitId) {
        logger.info("[Facade] 取消任务: {}", executionUnitId);

        TaskOperationResult result = taskApplicationService.cancelTask(executionUnitId);
        handleTaskOperationResult(result, "取消任务");

        logger.info("[Facade] 任务取消成功: {}", executionUnitId);
    }

    /**
     * 根��租户 ID 取消任务
     */
    public void cancelTaskByTenant(String tenantId) {
        logger.info("[Facade] 取消租户任务: {}", tenantId);

        TaskOperationResult result = taskApplicationService.cancelTaskByTenant(tenantId);
        handleTaskOperationResult(result, "取消任务");

        logger.info("[Facade] 租户任务取消成功: {}", tenantId);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 处理 Task 操作结果，失败时抛出异常
     */
    private void handleTaskOperationResult(TaskOperationResult result, String operation) {
        if (!result.isSuccess()) {
            FailureInfo failureInfo = result.getFailureInfo();
            String message = result.getMessage();

            // 区分不同类型的错误
            if (message != null && message.contains("未找到")) {
                throw new TaskNotFoundException(message);
            }

            throw new TaskOperationException(
                operation + "失败: " + message,
                failureInfo
            );
        }
    }

    /**
     * 处理 Plan 操作结果，失败时抛出异常
     */
    private void handlePlanOperationResult(PlanOperationResult result, String operation) {
        if (!result.isSuccess()) {
            FailureInfo failureInfo = result.getFailureInfo();
            String message = result.getMessage();

            // 区分不同类型的错误
            if (message != null && message.contains("不存在")) {
                throw new PlanNotFoundException(message);
            }

            throw new TaskOperationException(
                operation + "失败: " + message,
                failureInfo
            );
        }
    }

    /**
     * 格式化校验错误信息
     */
    private String formatValidationErrors(xyz.firestige.executor.validation.ValidationSummary summary) {
        List<ValidationError> errors = summary.getAllErrors();
        if (errors.isEmpty()) {
            return "未知校验错误";
        }

        return errors.stream()
                .limit(5) // 最多显示 5 个错误
                .map(e -> String.format("[%s] %s", e.getField(), e.getMessage()))
                .collect(Collectors.joining("; "));
    }
}

