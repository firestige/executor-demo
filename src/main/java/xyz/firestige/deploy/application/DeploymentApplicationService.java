package xyz.firestige.deploy.application;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.application.plan.DeploymentPlanCreator;
import xyz.firestige.deploy.application.plan.PlanCreationContext;
import xyz.firestige.deploy.application.plan.PlanCreationException;
import xyz.firestige.deploy.domain.plan.PlanCreationResult;
import xyz.firestige.deploy.domain.plan.PlanDomainService;
import xyz.firestige.deploy.domain.plan.PlanOperationResult;
import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.TaskOperationResult;
import xyz.firestige.deploy.exception.ErrorType;
import xyz.firestige.deploy.exception.FailureInfo;
import xyz.firestige.deploy.execution.TaskExecutor;
import xyz.firestige.deploy.execution.TaskWorkerCreationContext;
import xyz.firestige.deploy.execution.TaskWorkerFactory;
import xyz.firestige.deploy.facade.TaskStatusInfo;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.state.TaskStatus;
import xyz.firestige.deploy.support.conflict.TenantConflictManager;

/**
 * 部署应用服务（RF-17: 依赖注入优化版）
 *
 * 职责：
 * 1. 协调各种部署操作（创建、暂停、恢复等）
 * 2. 委托具体逻辑给专门的组件（DeploymentPlanCreator、DomainService）
 * 3. 统一返回 Result DTOs
 * 4. 异常处理和日志记录
 * 5. 事务边界管理（RF-12）
 * 6. 租户冲突检测（RF-14：合并策略）
 * 7. 执行器创建协调（RF-17：通过工厂封装）
 *
 * 设计说明：
 * - RF-10 重构：提取 DeploymentPlanCreator，简化职责
 * - RF-12 重构：添加 @Transactional，集成调度策略
 * - RF-14 重构：合并 ConflictRegistry + PlanSchedulingStrategy
 * - RF-15 重构：从领域层接管 TaskExecutor 创建和执行职责
 * - RF-16 重构：引入 TaskExecutorFactory，依赖从 9 个减少到 6 个
 * - RF-17 重构：基础设施依赖注入到工厂，依赖从 6 个减少到 5 个
 * - 应用服务只做协调，不包含具体业务逻辑
 * - 单一职责：部署操作的统一入口
 *
 * @since RF-17: 基础设施依赖注入到 TaskWorkerFactory
 */
public class DeploymentApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentApplicationService.class);

    private final DeploymentPlanCreator deploymentPlanCreator;
    private final PlanDomainService planDomainService;
    private final TaskDomainService taskDomainService;
    private final TenantConflictManager conflictManager;
    // RF-17: 使用 TaskWorkerFactory 直接创建执行器
    private final TaskWorkerFactory taskWorkerFactory;
    // RF-16: 保留 stateManager 用于发布回滚失败事件
    private final TaskStateManager stateManager;

    public DeploymentApplicationService(
            DeploymentPlanCreator deploymentPlanCreator,
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            TenantConflictManager conflictManager,
            TaskWorkerFactory taskWorkerFactory,
            TaskStateManager stateManager) {
        this.deploymentPlanCreator = deploymentPlanCreator;
        this.planDomainService = planDomainService;
        this.taskDomainService = taskDomainService;
        this.conflictManager = conflictManager;
        this.taskWorkerFactory = taskWorkerFactory;
        this.stateManager = stateManager;
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
            // RF-14: 提取租户 ID
            List<String> tenantIds = configs.stream()
                .map(TenantConfig::getTenantId)
                .collect(Collectors.toList());

            // RF-14: 冲突检测（统一接口，纯内存操作，< 1ms）
            TenantConflictManager.ConflictCheckResult conflictCheck = conflictManager.canCreatePlan(tenantIds);
            if (!conflictCheck.isAllowed()) {
                logger.warn("[DeploymentApplicationService] 创建 Plan 被拒绝，冲突租户: {}", 
                    conflictCheck.getConflictingTenants());
                return PlanCreationResult.failure(
                    FailureInfo.of(ErrorType.CONFLICT, conflictCheck.getMessage()),
                    "请等待相关 Plan 完成，或移除冲突租户后重试"
                );
            }

            // 委托给 DeploymentPlanCreator 处理创建流程
            PlanCreationContext context = deploymentPlanCreator.createPlan(configs);

            // 检查验证结果
            if (context.hasValidationErrors()) {
                return PlanCreationResult.validationFailure(context.getValidationSummary());
            }

            // RF-14: 无需通知（TenantConflictManager 无状态，锁由 Task 注册时管理）

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
     * 根据租户 ID 回滚任务（RF-17: 应用层创建执行器）
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult rollbackTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 回滚租户任务: {}", tenantId);

        // Step 1: 调用领域服务准备回滚
        TaskWorkerCreationContext context = taskDomainService.prepareRollbackByTenant(tenantId);
        if (context == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // Step 2: 创建或复用 TaskExecutor
        TaskExecutor executor = context.hasExistingExecutor()
            ? context.getExistingExecutor()
            : createTaskExecutor(context);

        // Step 3: 执行回滚
        var result = executor.invokeRollback();

        // Step 4: 发布回滚结果事件
        TaskStatus finalStatus = context.getTask().getStatus();
        if (finalStatus == TaskStatus.ROLLED_BACK) {
            // 聚合产生的事件已由 TaskDomainService 发布
        } else if (finalStatus == TaskStatus.ROLLBACK_FAILED) {
            stateManager.publishTaskRollbackFailedEvent(
                context.getTask().getTaskId(),
                FailureInfo.of(ErrorType.SYSTEM_ERROR, "rollback failed"),
                null
            );
        }

        logger.info("[DeploymentApplicationService] 租户任务回滚结束: {}, status: {}",
                    tenantId, result.getFinalStatus());
        return TaskOperationResult.success(
            context.getTask().getTaskId(),
            finalStatus,
            "租户任务回滚结束: " + result.getFinalStatus()
        );
    }

    /**
     * 根据租户 ID 重试任务（RF-17: 应用层创建执行器）
     *
     * @param tenantId 租户 ID
     * @param fromCheckpoint 是否从 checkpoint 恢复
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult retryTaskByTenant(String tenantId, boolean fromCheckpoint) {
        logger.info("[DeploymentApplicationService] 重试租户任务: {}, fromCheckpoint: {}",
                    tenantId, fromCheckpoint);

        // Step 1: 调用领域服务准备重试
        TaskWorkerCreationContext context = taskDomainService.prepareRetryByTenant(tenantId, fromCheckpoint);
        if (context == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // Step 2: 创建或复用 TaskExecutor
        TaskExecutor executor = context.hasExistingExecutor()
            ? context.getExistingExecutor()
            : createTaskExecutor(context);

        // Step 3: 执行重试
        var result = executor.retry(fromCheckpoint);

        logger.info("[DeploymentApplicationService] 租户任务重试启动: {}, status: {}",
                    tenantId, result.getFinalStatus());
        return TaskOperationResult.success(
            context.getTask().getTaskId(),
            result.getFinalStatus(),
            "租户任务重试启动"
        );
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

    // ========== 辅助方法 (RF-17) ==========

    /**
     * 创建 TaskExecutor（RF-17: 直接委托给 TaskWorkerFactory）
     *
     * @param context Task 创建上下文
     * @return TaskExecutor 实例
     */
    private TaskExecutor createTaskExecutor(TaskWorkerCreationContext context) {
        return taskWorkerFactory.create(context);
    }
}

