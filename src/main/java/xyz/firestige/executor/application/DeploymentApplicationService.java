package xyz.firestige.executor.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import xyz.firestige.executor.application.dto.TenantConfig;
import xyz.firestige.executor.application.plan.DeploymentPlanCreator;
import xyz.firestige.executor.application.plan.PlanCreationContext;
import xyz.firestige.executor.application.plan.PlanCreationException;
import xyz.firestige.executor.domain.plan.*;
import xyz.firestige.executor.domain.task.TaskDomainService;
import xyz.firestige.executor.domain.task.TaskOperationResult;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.facade.TaskStatusInfo;
import xyz.firestige.executor.orchestration.strategy.PlanSchedulingStrategy;
import xyz.firestige.executor.support.conflict.ConflictRegistry;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 部署应用服务（DDD 重构：RF-10 优化版 + RF-12 事务管理 + 调度策略）
 *
 * 职责：
 * 1. 协调各种部署操作（创建、暂停、恢复等）
 * 2. 委托具体逻辑给专门的组件（DeploymentPlanCreator、DomainService）
 * 3. 统一返回 Result DTOs
 * 4. 异常处理和日志记录
 * 5. 事务边界管理（RF-12）
 * 6. 调度策略检查（RF-12）
 *
 * 设计说明：
 * - RF-10 重构：提取 DeploymentPlanCreator，简化职责
 * - RF-12 重构：添加 @Transactional，集成调度策略
 * - 应用服务只做协调，不包含具体业务逻辑
 * - 单一职责：部署操作的统一入口
 *
 * @since DDD 重构 Phase 18 - RF-12
 */
public class DeploymentApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentApplicationService.class);

    private final DeploymentPlanCreator deploymentPlanCreator;
    private final PlanDomainService planDomainService;
    private final TaskDomainService taskDomainService;
    private final PlanSchedulingStrategy schedulingStrategy;
    private final ConflictRegistry conflictRegistry;

    public DeploymentApplicationService(
            DeploymentPlanCreator deploymentPlanCreator,
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            PlanSchedulingStrategy schedulingStrategy,
            ConflictRegistry conflictRegistry) {
        this.deploymentPlanCreator = deploymentPlanCreator;
        this.planDomainService = planDomainService;
        this.taskDomainService = taskDomainService;
        this.schedulingStrategy = schedulingStrategy;
        this.conflictRegistry = conflictRegistry;
    }

    /**
     * 创建部署计划（RF-12：添加事务管理 + 调度策略检查）
     *
     * @param configs 租户配置列表（内部 DTO）
     * @return Plan 创建结果
     */
    @Transactional  // RF-12: 事务边界
    public PlanCreationResult createDeploymentPlan(List<TenantConfig> configs) {
        logger.info("[DeploymentApplicationService] 创建部署计划，租户数量: {}",
                configs != null ? configs.size() : 0);

        try {
            // RF-12: 提取租户 ID
            List<String> tenantIds = configs.stream()
                .map(TenantConfig::getTenantId)
                .collect(Collectors.toList());

            // RF-12: 调度策略检查（纯内存操作，< 1ms）
            if (!schedulingStrategy.canCreatePlan(tenantIds)) {
                // 找出冲突租户
                List<String> conflictTenants = tenantIds.stream()
                    .filter(tid -> conflictRegistry.hasConflict(tid))
                    .collect(Collectors.toList());

                logger.warn("[DeploymentApplicationService] 创建 Plan 被拒绝，冲突租户: {}", conflictTenants);
                return PlanCreationResult.failure(
                    FailureInfo.of(ErrorType.CONFLICT,
                        "租户冲突：以下租户已在运行中的 Plan 中: " + conflictTenants),
                    "请等待相关 Plan 完成，或移除冲突租户后重试"
                );
            }

            // 委托给 DeploymentPlanCreator 处理创建流程
            PlanCreationContext context = deploymentPlanCreator.createPlan(configs);

            // 检查验证结果
            if (context.hasValidationErrors()) {
                return PlanCreationResult.validationFailure(context.getValidationSummary());
            }

            // RF-12: 通知调度策略 Plan 已创建
            schedulingStrategy.onPlanCreated(context.getPlanId(), tenantIds);

            // 返回成功结果（事务自动提交）
            return PlanCreationResult.success(context.getPlanInfo());

        } catch (PlanCreationException e) {
            logger.error("[DeploymentApplicationService] 创建部署计划失败", e);
            return PlanCreationResult.failure(
                    FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
                    "创建失败: " + e.getMessage()
            );
        } catch (Exception e) {
            logger.error("[DeploymentApplicationService] 创建部署计划发生未知错误", e);
            return PlanCreationResult.failure(
                    FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
                    "系统错误: " + e.getMessage()
            );
        }
    }

    // ========== Plan 级别操作（委托给 PlanDomainService）==========

    /**
     * 暂停部署计划（RF-12: 添加事务管理）
     *
     * @param planId 计划 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public PlanOperationResult pausePlan(Long planId) {
        logger.info("[DeploymentApplicationService] 暂停计划: {}", planId);

        String planIdStr = String.valueOf(planId);
        try {
            planDomainService.pausePlanExecution(planIdStr);
            return PlanOperationResult.success(planIdStr, PlanStatus.PAUSED, "计划已暂停");
        } catch (Exception e) {
            logger.error("[DeploymentApplicationService] 暂停计划失败: {}", planId, e);
            return PlanOperationResult.failure(
                planIdStr,
                FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
                "暂停失败"
            );
        }
    }

    /**
     * 恢复部署计划（RF-12: 添加事务管理）
     *
     * @param planId 计划 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public PlanOperationResult resumePlan(Long planId) {
        logger.info("[DeploymentApplicationService] 恢复计划: {}", planId);

        String planIdStr = String.valueOf(planId);
        try {
            planDomainService.resumePlanExecution(planIdStr);
            return PlanOperationResult.success(planIdStr, PlanStatus.RUNNING, "计划已恢复");
        } catch (Exception e) {
            logger.error("[DeploymentApplicationService] 恢复计划失败: {}", planId, e);
            return PlanOperationResult.failure(
                planIdStr,
                FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
                "恢复失败"
            );
        }
    }

    // ========== Task 级别操作（委托给 TaskDomainService）==========

    /**
     * 根据租户 ID 暂停任务（RF-12: 添加事务管理）
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult pauseTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 暂停租户任务: {}", tenantId);
        return taskDomainService.pauseTaskByTenant(tenantId);
    }

    /**
     * 根据租户 ID 恢复任务（RF-12: 添加事务管理）
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult resumeTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 恢复租户任务: {}", tenantId);
        return taskDomainService.resumeTaskByTenant(tenantId);
    }

    /**
     * 根据租户 ID 回滚任务（RF-12: 添加事务管理）
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult rollbackTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 回滚租户任务: {}", tenantId);
        return taskDomainService.rollbackTaskByTenant(tenantId);
    }

    /**
     * 根据租户 ID 重试任务（RF-12: 添加事务管理）
     *
     * @param tenantId 租户 ID
     * @param fromCheckpoint 是否从 checkpoint 恢复
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult retryTaskByTenant(String tenantId, boolean fromCheckpoint) {
        logger.info("[DeploymentApplicationService] 重试租户任务: {}, fromCheckpoint: {}",
                    tenantId, fromCheckpoint);
        return taskDomainService.retryTaskByTenant(tenantId, fromCheckpoint);
    }

    /**
     * 根据租户 ID 取消任务（RF-12: 添加事务管理）
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult cancelTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 取消租户任务: {}", tenantId);
        return taskDomainService.cancelTaskByTenant(tenantId);
    }

    /**
     * 查询任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态信息
     */
    public TaskStatusInfo queryTaskStatus(String taskId) {
        logger.debug("[DeploymentApplicationService] 查询任务状态: {}", taskId);
        return taskDomainService.queryTaskStatus(taskId);
    }

    /**
     * 根据租户 ID 查询任务状态
     *
     * @param tenantId 租户 ID
     * @return 任务状态信息
     */
    public TaskStatusInfo queryTaskStatusByTenant(String tenantId) {
        logger.debug("[DeploymentApplicationService] 查询租户任务状态: {}", tenantId);
        return taskDomainService.queryTaskStatusByTenant(tenantId);
    }
}

