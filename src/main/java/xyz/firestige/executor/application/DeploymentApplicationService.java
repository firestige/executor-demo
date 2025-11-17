package xyz.firestige.executor.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.application.dto.TenantConfig;
import xyz.firestige.executor.domain.plan.*;
import xyz.firestige.executor.domain.stage.StageFactory;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskDomainService;
import xyz.firestige.executor.domain.task.TaskOperationResult;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.facade.TaskStatusInfo;
import xyz.firestige.executor.service.health.HealthCheckClient;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.validation.ValidationSummary;

import java.util.List;

/**
 * 部署应用服务（DDD 重构完成版）
 *
 * 职责：
 * 1. 协调 Plan 和 Task 的创建（跨聚合操作）
 * 2. 业务流程编排
 * 3. 业务校验
 * 4. 事务边界控制
 * 5. 返回 Result DTOs
 *
 * 设计说明：
 * - 这是真正的应用服务层
 * - 协调多个领域服务完成业务流程
 * - 不包含领域逻辑，只做编排
 * - 处理跨聚合的业务场景
 *
 * @since DDD 重构 Phase 2.2 - 完成版
 */
public class DeploymentApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentApplicationService.class);

    private final PlanDomainService planDomainService;
    private final TaskDomainService taskDomainService;
    private final ValidationChain validationChain;
    // 依赖其他基础设施服务用于 Stage 构建
    private final StageFactory stageFactory;
    private final HealthCheckClient healthCheckClient;

    public DeploymentApplicationService(
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            ValidationChain validationChain,
            StageFactory stageFactory,
            HealthCheckClient healthCheckClient) {
        this.planDomainService = planDomainService;
        this.taskDomainService = taskDomainService;
        this.validationChain = validationChain;
        this.stageFactory = stageFactory;
        this.healthCheckClient = healthCheckClient;
    }

    /**
     * 创建部署计划（协调 Plan 和 Task 创建）- 完整实现
     *
     * @param configs 租户配置列表（外部 DTO）
     * @return Plan 创建结果
     */
    public PlanCreationResult createDeploymentPlan(List<TenantConfig> configs) {
        logger.info("[DeploymentApplicationService] 创建部署计划，租户数量: {}",
                    configs != null ? configs.size() : 0);

        try {
            // Step 1: 参数校验
            if (configs == null || configs.isEmpty()) {
                return PlanCreationResult.failure(
                    null,
                    FailureInfo.of(ErrorType.VALIDATION_ERROR, "配置列表不能为空"),
                    "配置列表为空"
                );
            }

            // Step 2: 业务校验（应用层职责）
            ValidationSummary validationSummary = validationChain.validateAll(configs);
            if (validationSummary.hasErrors()) {
                logger.warn("[DeploymentApplicationService] 配置校验失败，无效配置数: {}",
                           validationSummary.getInvalidCount());
                return PlanCreationResult.validationFailure(validationSummary);
            }

            // Step 3: 生成 Plan ID
            String planId = PlanDomainService.generatePlanId();
            logger.info("[DeploymentApplicationService] 生成 Plan ID: {}", planId);

            // Step 4: 创建 Plan（委托给 PlanDomainService）
            PlanAggregate plan = planDomainService.createPlan(planId, configs.size());

            // Step 5: 为每个租户创建 Task（委托给 TaskDomainService）
            for (TenantConfig config : configs) {
                // 创建 Task 聚合
                TaskAggregate task = taskDomainService.createTask(planId, config);

                // 构建 Task 的 Stages
                taskDomainService.buildTaskStages(task, config, stageFactory, healthCheckClient);

                // 关联 Task 到 Plan（跨聚合协调 - 应用层职责）
                planDomainService.addTaskToPlan(planId, task);

                logger.debug("[DeploymentApplicationService] Task 创建并关联成功: {}", task.getTaskId());
            }

            // Step 6: 启动 Plan 执行（委托给 PlanDomainService）
            planDomainService.startPlan(planId);

            // Step 7: 返回结果
            PlanInfo planInfo = planDomainService.getPlanInfo(planId);
            logger.info("[DeploymentApplicationService] 部署计划创建成功，planId: {}", planId);

            return PlanCreationResult.success(planId, planInfo);

        } catch (Exception e) {
            logger.error("[DeploymentApplicationService] 创建部署计划失败", e);
            return PlanCreationResult.failure(
                null,
                FailureInfo.of(ErrorType.INTERNAL_ERROR, e.getMessage()),
                "创建失败: " + e.getMessage()
            );
        }
    }

    // ========== Plan 级别操作（委托给 PlanDomainService）==========

    /**
     * 暂停部署计划
     *
     * @param planId 计划 ID
     * @return 操作结果
     */
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
                FailureInfo.of(ErrorType.INTERNAL_ERROR, e.getMessage()),
                "暂停失败"
            );
        }
    }

    /**
     * 恢复部署计划
     *
     * @param planId 计划 ID
     * @return 操作结果
     */
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
                FailureInfo.of(ErrorType.INTERNAL_ERROR, e.getMessage()),
                "恢复失败"
            );
        }
    }

    // ========== Task 级别操作（委托给 TaskDomainService）==========

    /**
     * 根据租户 ID 暂停任务
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    public TaskOperationResult pauseTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 暂停租户任务: {}", tenantId);
        return taskDomainService.pauseTaskByTenant(tenantId);
    }

    /**
     * 根据租户 ID 恢复任务
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    public TaskOperationResult resumeTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 恢复租户任务: {}", tenantId);
        return taskDomainService.resumeTaskByTenant(tenantId);
    }

    /**
     * 根据租户 ID 回滚任务
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    public TaskOperationResult rollbackTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 回滚租户任务: {}", tenantId);
        return taskDomainService.rollbackTaskByTenant(tenantId);
    }

    /**
     * 根据租户 ID 重试任务
     *
     * @param tenantId 租户 ID
     * @param fromCheckpoint 是否从 checkpoint 恢复
     * @return 操作结果
     */
    public TaskOperationResult retryTaskByTenant(String tenantId, boolean fromCheckpoint) {
        logger.info("[DeploymentApplicationService] 重试租户任务: {}, fromCheckpoint: {}",
                    tenantId, fromCheckpoint);
        return taskDomainService.retryTaskByTenant(tenantId, fromCheckpoint);
    }

    /**
     * 根据租户 ID 取消任务
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
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

