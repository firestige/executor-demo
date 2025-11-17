package xyz.firestige.executor.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.executor.application.dto.TenantConfig;
import xyz.firestige.executor.domain.plan.*;
import xyz.firestige.executor.domain.task.TaskDomainService;
import xyz.firestige.executor.domain.task.TaskInfo;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.validation.ValidationSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 部署应用服务 (DDD 重构)
 *
 * 职责：
 * 1. 协调 Plan 和 Task 的创建（跨聚合操作）
 * 2. 业务流程编排
 * 3. 事务边界控制
 * 4. 返回 Result DTOs
 *
 * 设计说明：
 * - 这是真正的应用服务层
 * - 协调多个领域服务完成业务流程
 * - 不包含领域逻辑，只做编排
 *
 * @since DDD 重构 Phase 2.2.4
 */
public class DeploymentApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentApplicationService.class);
    private static final AtomicLong PLAN_ID_GENERATOR = new AtomicLong(System.currentTimeMillis());

    private final PlanDomainService planDomainService;
    private final TaskDomainService taskDomainService;
    private final ValidationChain validationChain;

    public DeploymentApplicationService(
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            ValidationChain validationChain) {
        this.planDomainService = planDomainService;
        this.taskDomainService = taskDomainService;
        this.validationChain = validationChain;
    }

    /**
     * 创建部署计划（协调 Plan 和 Task 创建）
     *
     * TODO: 当前暂时委托给 PlanDomainService.createSwitchTask()
     * 后续需要重构为：
     * 1. 调用 ValidationChain 验证
     * 2. 调用 PlanDomainService.createPlan() 创建 Plan
     * 3. 循环调用 TaskDomainService.createTask() 创建每个 Task
     * 4. 调用 PlanDomainService.startPlan() 启动执行
     * 5. 返回 PlanCreationResult
     *
     * @param configs 租户配置列表（内部 DTO）
     * @return Plan 创建结果
     */
    public PlanCreationResult createDeploymentPlan(List<TenantConfig> configs) {
        logger.info("[DeploymentApplicationService] 创建部署计划，租户数量: {}",
                    configs != null ? configs.size() : 0);

        // TODO: 暂时委托给 PlanDomainService，等后续完全重构
        // 这里需要将 TenantConfig 转换为 TenantDeployConfig
        // 当前先返回一个占位实现

        logger.warn("[DeploymentApplicationService] 暂未实现，返回占位结果");
        return PlanCreationResult.failure(
            null,
            xyz.firestige.executor.exception.FailureInfo.of(
                xyz.firestige.executor.exception.ErrorType.INTERNAL_ERROR,
                "DeploymentApplicationService 暂未完全实现"
            ),
            "功能开发中"
        );
    }

    /**
     * 暂停部署计划
     *
     * @param planId 计划 ID
     * @return 操作结果
     */
    public PlanOperationResult pausePlan(Long planId) {
        logger.info("[DeploymentApplicationService] 暂停计划: {}", planId);
        return planDomainService.pausePlan(planId);
    }

    /**
     * 恢复部署计划
     *
     * @param planId 计划 ID
     * @return 操作结果
     */
    public PlanOperationResult resumePlan(Long planId) {
        logger.info("[DeploymentApplicationService] 恢复计划: {}", planId);
        return planDomainService.resumePlan(planId);
    }

    /**
     * 回滚部署计划
     *
     * @param planId 计划 ID
     * @return 操作结果
     */
    public PlanOperationResult rollbackPlan(Long planId) {
        logger.info("[DeploymentApplicationService] 回滚计划: {}", planId);
        return planDomainService.rollbackPlan(planId);
    }

    /**
     * 重试部署计划
     *
     * @param planId 计划 ID
     * @param fromCheckpoint 是否从 checkpoint 恢复
     * @return 操作结果
     */
    public PlanOperationResult retryPlan(Long planId, boolean fromCheckpoint) {
        logger.info("[DeploymentApplicationService] 重试计划: {}, fromCheckpoint: {}",
                    planId, fromCheckpoint);
        return planDomainService.retryPlan(planId, fromCheckpoint);
    }

    // TODO: 添加更多协调方法
    // - pauseTaskByTenant() - 委托给 TaskDomainService
    // - resumeTaskByTenant() - 委托给 TaskDomainService
    // - cancelTaskByTenant() - 委托给 TaskDomainService
    // - rollbackTaskByTenant() - 委托给 TaskDomainService
    // - retryTaskByTenant() - 委托给 TaskDomainService
    // - queryTaskStatus() - 委托给 TaskDomainService
}

