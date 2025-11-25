package xyz.firestige.deploy.facade;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.application.lifecycle.PlanLifecycleService;
import xyz.firestige.deploy.application.task.TaskOperationService;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.plan.PlanCreationResult;
import xyz.firestige.deploy.domain.plan.PlanInfo;
import xyz.firestige.deploy.domain.plan.PlanOperationResult;
import xyz.firestige.deploy.domain.task.TaskOperationResult;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.facade.converter.TenantConfigConverter;
import xyz.firestige.deploy.facade.exception.PlanNotFoundException;
import xyz.firestige.deploy.facade.exception.TaskCreationException;
import xyz.firestige.deploy.facade.exception.TaskNotFoundException;
import xyz.firestige.deploy.facade.exception.TaskOperationException;
import xyz.firestige.deploy.application.query.TaskQueryService;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjection;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部署任务 Facade（RF-20: 服务拆分优化版）
 * <p>
 * 职责：
 * 1. DTO 转换：外部 DTO (TenantDeployConfig) → 内部 DTO（当前直接使用，后续可优化）
 * 2. 参数校验（快速失败）
 * 3. 调用应用服务（PlanLifecycleService, TaskOperationService）
 * 4. 异常转换：应用层 Result → Facade 异常
 * <p>
 * 设计说明：
 * - 不定义接口，直接使用具体类（符合 YAGNI 原则）
 * - 返回 void（查询操作除外），通过异常机制处理错误
 * - 保护应用层接口稳定，外部 DTO 变化不影响应用层
 * - RF-20: 拆分后依赖 PlanLifecycleService 和 TaskOperationService
 *
 * @since RF-20 - 服务拆分
 */
@Component
public class DeploymentTaskFacade {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentTaskFacade.class);

    private final PlanLifecycleService planLifecycleService;
    private final TaskOperationService taskOperationService;
    private final TenantConfigConverter tenantConfigConverter;
    private final Validator validator;  // Jakarta Validator
    private final TaskQueryService taskQueryService; // T-016 投影查询服务

    public DeploymentTaskFacade(
            PlanLifecycleService planLifecycleService,
            TaskOperationService taskOperationService,
            TenantConfigConverter tenantConfigConverter,
            Validator validator,
            TaskQueryService taskQueryService) { // 新增注入
        this.planLifecycleService = planLifecycleService;
        this.taskOperationService = taskOperationService;
        this.tenantConfigConverter = tenantConfigConverter;
        this.validator = validator;
        this.taskQueryService = taskQueryService;
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

        // Step 2: DTO 转换：外部 DTO → 内部 DTO（防腐层职责）
        List<TenantConfig> internalConfigs = tenantConfigConverter.fromExternal(configs);

        // Step 3: 字段格式校验（对转换后的 TenantConfig 使用 Spring Validator）
        for (int i = 0; i < internalConfigs.size(); i++) {
            TenantConfig config = internalConfigs.get(i);
            Set<ConstraintViolation<TenantConfig>> violations = validator.validate(config);

            if (!violations.isEmpty()) {
                String errorDetail = violations.stream()
                        .map(v -> String.format("[%s] %s", v.getPropertyPath(), v.getMessage()))
                        .collect(Collectors.joining("; "));

                logger.warn("[Facade] TenantConfig 格式校验失败 (索引 {}): {}", i, errorDetail);
                throw new IllegalArgumentException("TenantConfig 格式校验失败: " + errorDetail);
            }
        }

        // Step 4: 调用应用服务（使用内部 DTO）
        // 应用服务内部会执行业务规则校验（BusinessValidator）
        PlanCreationResult result = planLifecycleService.createDeploymentPlan(internalConfigs);

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
        TaskOperationResult result = taskOperationService.pauseTaskByTenant(TenantId.of(tenantId));
        handleTaskOperationResult(result, "暂停任务");
        logger.info("[Facade] 租户任务暂停成功: {}", tenantId);
    }

    /**
     * 根据计划 ID 暂停任务
     */
    public void pauseTaskByPlan(PlanId planId) {
        logger.info("[Facade] 暂停计划: {}", planId);
        PlanOperationResult result = planLifecycleService.pausePlan(planId);
        handlePlanOperationResult(result, "暂停计划");
        logger.info("[Facade] 计划暂停成功: {}", planId);
    }

    /**
     * 根据租户 ID 恢复任务
     */
    public void resumeTaskByTenant(TenantId tenantId) {
        logger.info("[Facade] 恢复租户任务: {}", tenantId);
        TaskOperationResult result = taskOperationService.resumeTaskByTenant(tenantId);
        handleTaskOperationResult(result, "恢复任务");
        logger.info("[Facade] 租户任务恢复成功: {}", tenantId);
    }

    /**
     * 根据计划 ID 恢复任务
     */
    public void resumeTaskByPlan(PlanId planId) {
        logger.info("[Facade] 恢复计划: {}", planId);
        PlanOperationResult result = planLifecycleService.resumePlan(planId);
        handlePlanOperationResult(result, "恢复计划");
        logger.info("[Facade] 计划恢复成功: {}", planId);
    }

    /**
     * 根据租户 ID 回滚任务
     */
    public void rollbackTaskByTenant(TenantId tenantId) {
        logger.info("[DeploymentTaskFacade] 回滚租户任务: {}", tenantId);
        TaskOperationResult result = taskOperationService.rollbackTaskByTenant(tenantId);  // T-015: 异步执行，监听领域事件
        handleTaskOperationResult(result, "回滚任务");
        logger.info("[Facade] 租户任务回滚成功: {}", tenantId);
    }

    /**
     * 根据租户 ID 重试任务
     */
    public void retryTaskByTenant(TenantId tenantId, boolean fromCheckpoint) {
        logger.info("[DeploymentTaskFacade] 重试租户任务: {}, fromCheckpoint: {}", tenantId, fromCheckpoint);
        TaskOperationResult result = taskOperationService.retryTaskByTenant(tenantId, fromCheckpoint);  // T-015: 异步执行，监听领域事件
        handleTaskOperationResult(result, "重试任务");
        logger.info("[Facade] 租户任务重试成功: {}", tenantId);
    }

    /**
     * 查询任务状态
     */
    public TaskStatusInfo queryTaskStatus(TaskId taskId) {
        logger.debug("[Facade] 查询任务状态: {}", taskId);
        TaskStatusInfo result = taskOperationService.queryTaskStatus(taskId);
        if (result.getStatus() == null) {
            throw new TaskNotFoundException("任务不存在: " + taskId);
        }
        return result;
    }

    /**
     * 根据租户 ID 查询任务状态
     */
    public TaskStatusInfo queryTaskStatusByTenant(TenantId tenantId) {
        logger.debug("[Facade] 查询租户任务状态: {}", tenantId);
        TaskStatusInfo result = taskOperationService.queryTaskStatusByTenant(tenantId);
        if (result.getStatus() == null) {
            throw new TaskNotFoundException("租户任务不存在: " + tenantId);
        }
        return result;
    }

    /**
     * 根据租户 ID 取消任务
     */
    public void cancelTaskByTenant(TenantId tenantId) {
        logger.info("[Facade] 取消租户任务: {}", tenantId);
        TaskOperationResult result = taskOperationService.cancelTaskByTenant(tenantId);
        handleTaskOperationResult(result, "取消任务");
        logger.info("[Facade] 租户任务取消成功: {}", tenantId);
    }

    /**
     * 查询计划状态（最小兜底 API）
     */
    public PlanStatusInfo queryPlanStatus(PlanId planId) {
        logger.debug("[Facade] 查询计划状态: {}", planId);
        var projection = taskQueryService.queryPlanStatus(planId);
        if (projection == null) {
            throw new PlanNotFoundException("计划不存在: " + planId);
        }
        return PlanStatusInfo.fromProjection(projection);
    }

    /**
     * 检查租户是否存在 Checkpoint（最小兜底 API）
     */
    public boolean hasCheckpoint(TenantId tenantId) {
        return taskQueryService.hasCheckpoint(tenantId);
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
