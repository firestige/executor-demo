package xyz.firestige.executor.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.application.DeploymentApplicationService;
import xyz.firestige.executor.application.dto.TenantConfig;
import xyz.firestige.executor.domain.plan.PlanCreationResult;
import xyz.firestige.executor.domain.plan.PlanInfo;
import xyz.firestige.executor.domain.plan.PlanOperationResult;
import xyz.firestige.executor.domain.task.TaskOperationResult;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.facade.converter.TenantConfigConverter;
import xyz.firestige.executor.facade.exception.PlanNotFoundException;
import xyz.firestige.executor.facade.exception.TaskCreationException;
import xyz.firestige.executor.facade.exception.TaskNotFoundException;
import xyz.firestige.executor.facade.exception.TaskOperationException;
import xyz.firestige.executor.validation.ValidationError;
import xyz.firestige.executor.validation.ValidationSummary;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 部署任务 Facade（DDD 重构完成版）
 * <p>
 * 职责：
 * 1. DTO 转换：外部 DTO (TenantDeployConfig) → 内部 DTO（当前直接使用，后续可优化）
 * 2. 参数校验（快速失败）
 * 3. 调用应用服务（DeploymentApplicationService）
 * 4. 异常转换：应用层 Result → Facade 异常
 * <p>
 * 设计说明：
 * - 不定义接口，直接使用具体类（符合 YAGNI 原则）
 * - 返回 void（查询操作除外），通过异常机制处理错误
 * - 保护应用层接口稳定，外部 DTO 变化不影响应用层
 *
 * @since DDD 重构 Phase 2.3 - 完成版
 */
@Component
public class DeploymentTaskFacade {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentTaskFacade.class);

    private final DeploymentApplicationService deploymentApplicationService;

    public DeploymentTaskFacade(
            DeploymentApplicationService deploymentApplicationService) {
        this.deploymentApplicationService = deploymentApplicationService;
    }

    /**
     * 创建切换任务
     */
    public void createSwitchTask(List<TenantDeployConfig> configs) {
        logger.info("[Facade] 创建切换任务，配置数量: {}", configs != null ? configs.size() : 0);

        // Step 1: 参数校验（快速失败）
        if (configs == null || configs.isEmpty()) {
            throw new IllegalArgumentException("配置列表不能为空");
        }

        // Step 2: 业务校验（使用 ValidationChain）
        // ValidationChain 在 Facade 层完成，避免无效数据进入应用层
        ValidationSummary validationSummary = validationChain.validateAll(configs);
        if (validationSummary.hasErrors()) {
            String errorDetail = formatValidationErrors(validationSummary);
            logger.warn("[Facade] 配置校验失败: {}", errorDetail);
            throw new IllegalArgumentException("配置校验失败: " + errorDetail);
        }

        // Step 3: DTO 转换：外部 DTO → 内部 DTO（防腐层职责）
        List<TenantConfig> internalConfigs = TenantConfigConverter.fromExternal(configs);

        // Step 4: 调用应用服务（使用内部 DTO）
        PlanCreationResult result = deploymentApplicationService.createDeploymentPlan(internalConfigs);

        // Step 5: 处理结果
        if (!result.isSuccess()) {

            FailureInfo failureInfo = result.getFailureInfo();
            throw new TaskCreationException(
                failureInfo != null ? failureInfo.getErrorMessage() : "任务创建失败",
                failureInfo
            );
        }

        PlanInfo planInfo = result.getPlanInfo();
        logger.info("[Facade] Plan 创建成功，planId: {}, tasks: {}",
                    planInfo.getPlanId(), planInfo.getTasks().size());
    }

    /**
     * 根据租户 ID 暂停任务
     */
    public void pauseTaskByTenant(String tenantId) {
        logger.info("[Facade] 暂停租户任务: {}", tenantId);
        TaskOperationResult result = deploymentApplicationService.pauseTaskByTenant(tenantId);
        handleTaskOperationResult(result, "暂停任务");
        logger.info("[Facade] 租户任务暂停成功: {}", tenantId);
    }

    /**
     * 根据计划 ID 暂停任务
     */
    public void pauseTaskByPlan(Long planId) {
        logger.info("[Facade] 暂停计划: {}", planId);
        PlanOperationResult result = deploymentApplicationService.pausePlan(planId);
        handlePlanOperationResult(result, "暂停计划");
        logger.info("[Facade] 计划暂停成功: {}", planId);
    }

    /**
     * 根据租户 ID 恢复任务
     */
    public void resumeTaskByTenant(String tenantId) {
        logger.info("[Facade] 恢复租户任务: {}", tenantId);
        TaskOperationResult result = deploymentApplicationService.resumeTaskByTenant(tenantId);
        handleTaskOperationResult(result, "恢复任务");
        logger.info("[Facade] 租户任务恢复成功: {}", tenantId);
    }

    /**
     * 根据计划 ID 恢复任务
     */
    public void resumeTaskByPlan(Long planId) {
        logger.info("[Facade] 恢复计划: {}", planId);
        PlanOperationResult result = deploymentApplicationService.resumePlan(planId);
        handlePlanOperationResult(result, "恢复计划");
        logger.info("[Facade] 计划恢复成功: {}", planId);
    }

    /**
     * 根据租户 ID 回滚任务
     */
    public void rollbackTaskByTenant(String tenantId) {
        logger.info("[Facade] 回滚租户任务: {}", tenantId);
        TaskOperationResult result = deploymentApplicationService.rollbackTaskByTenant(tenantId);
        handleTaskOperationResult(result, "回滚任务");
        logger.info("[Facade] 租户任务回滚成功: {}", tenantId);
    }

    /**
     * 根据租户 ID 重试任务
     */
    public void retryTaskByTenant(String tenantId, boolean fromCheckpoint) {
        logger.info("[Facade] 重试租户任务: {}, fromCheckpoint: {}", tenantId, fromCheckpoint);
        TaskOperationResult result = deploymentApplicationService.retryTaskByTenant(tenantId, fromCheckpoint);
        handleTaskOperationResult(result, "重试任务");
        logger.info("[Facade] 租户任务重试成功: {}", tenantId);
    }

    /**
     * 查询任务状态
     */
    public TaskStatusInfo queryTaskStatus(String executionUnitId) {
        logger.debug("[Facade] 查询任务状态: {}", executionUnitId);
        TaskStatusInfo result = deploymentApplicationService.queryTaskStatus(executionUnitId);
        if (result.getStatus() == null) {
            throw new TaskNotFoundException("任务不存在: " + executionUnitId);
        }
        return result;
    }

    /**
     * 根据租户 ID 查询任务状态
     */
    public TaskStatusInfo queryTaskStatusByTenant(String tenantId) {
        logger.debug("[Facade] 查询租户任务状态: {}", tenantId);
        TaskStatusInfo result = deploymentApplicationService.queryTaskStatusByTenant(tenantId);
        if (result.getStatus() == null) {
            throw new TaskNotFoundException("租户任务不存在: " + tenantId);
        }
        return result;
    }

    /**
     * 根据租户 ID 取消任务
     */
    public void cancelTaskByTenant(String tenantId) {
        logger.info("[Facade] 取消租户任务: {}", tenantId);
        TaskOperationResult result = deploymentApplicationService.cancelTaskByTenant(tenantId);
        handleTaskOperationResult(result, "取消任务");
        logger.info("[Facade] 租户任务取消成功: {}", tenantId);
    }

    // ========== 私有辅助方法 ==========

    private void handleTaskOperationResult(TaskOperationResult result, String operation) {
        if (!result.isSuccess()) {
            FailureInfo failureInfo = result.getFailureInfo();
            String message = result.getMessage();
            if (message != null && message.contains("未找到")) {
                throw new TaskNotFoundException(message);
            }
            throw new TaskOperationException(operation + "失败: " + message, failureInfo);
        }
    }

    private void handlePlanOperationResult(PlanOperationResult result, String operation) {
        if (!result.isSuccess()) {
            FailureInfo failureInfo = result.getFailureInfo();
            String message = result.getMessage();
            if (message != null && message.contains("不存在")) {
                throw new PlanNotFoundException(message);
            }
            throw new TaskOperationException(operation + "失败: " + message, failureInfo);
        }
    }
}
