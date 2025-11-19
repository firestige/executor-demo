package xyz.firestige.deploy.application.lifecycle;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.constraints.NotNull;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.application.plan.DeploymentPlanCreator;
import xyz.firestige.deploy.application.plan.PlanCreationContext;
import xyz.firestige.deploy.application.plan.PlanCreationException;
import xyz.firestige.deploy.domain.plan.PlanCreationResult;
import xyz.firestige.deploy.domain.plan.PlanDomainService;
import xyz.firestige.deploy.domain.plan.PlanOperationResult;
import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.PlanId;

/**
 * 计划生命周期服务（RF-20: DeploymentApplicationService 拆分）
 * <p>
 * 职责：
 * 1. 创建部署计划
 * 2. 验证计划配置
 * 3. 暂停/恢复/完成计划
 * <p>
 * 依赖（2个）：
 * - DeploymentPlanCreator：计划创建器
 * - PlanDomainService：计划领域服务
 * <p>
 * 设计说明：
 * - 聚焦于计划的生命周期管理
 * - 不涉及任务执行和编排
 * - 事务边界在方法级别
 *
 * @since RF-20 - 服务拆分
 */
public class PlanLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(PlanLifecycleService.class);

    private final DeploymentPlanCreator deploymentPlanCreator;
    private final PlanDomainService planDomainService;

    public PlanLifecycleService(
            DeploymentPlanCreator deploymentPlanCreator,
            PlanDomainService planDomainService) {
        this.deploymentPlanCreator = deploymentPlanCreator;
        this.planDomainService = planDomainService;
        
        logger.info("[PlanLifecycleService] 初始化完成");
    }

    /**
     * 创建部署计划
     *
     * @param configs 租户配置列表
     * @return Plan 创建结果
     */
    @Transactional
    public PlanCreationResult createDeploymentPlan(@NotNull List<TenantConfig> configs) {
        logger.info("[PlanLifecycleService] 创建部署计划，租户数量: {}",
                configs != null ? configs.size() : 0);

        try {
            // 委托给 DeploymentPlanCreator 处理创建流程
            PlanCreationContext context = deploymentPlanCreator.createPlan(configs);

            // 检查验证结果
            if (context.hasValidationErrors()) {
                return PlanCreationResult.validationFailure(context.getValidationSummary());
            }

            // 返回成功结果（事务自动提交）
            return PlanCreationResult.success(context.getPlanInfo());

        } catch (PlanCreationException e) {
            logger.error("[PlanLifecycleService] 创建部署计划失败", e);
            return PlanCreationResult.failure(
                    FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
                    "创建失败: " + e.getMessage()
            );
        } catch (Exception e) {
            logger.error("[PlanLifecycleService] 创建部署计划发生未知错误", e);
            return PlanCreationResult.failure(
                    FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
                    "系统错误: " + e.getMessage()
            );
        }
    }

    /**
     * 暂停部署计划
     *
     * @param planId 计划 ID
     * @return 操作结果
     */
    @Transactional
    public PlanOperationResult pausePlan(PlanId planId) {
        logger.info("[PlanLifecycleService] 暂停计划: {}", planId);

        try {
            planDomainService.pausePlanExecution(planId);
            return PlanOperationResult.success(planId.getValue(), PlanStatus.PAUSED, "计划已暂停");
        } catch (Exception e) {
            logger.error("[PlanLifecycleService] 暂停计划失败: {}", planId, e);
            return PlanOperationResult.failure(
                planId.getValue(),
                FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
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
    @Transactional
    public PlanOperationResult resumePlan(PlanId planId) {
        logger.info("[PlanLifecycleService] 恢复计划: {}", planId);

        try {
            planDomainService.resumePlanExecution(planId);
            return PlanOperationResult.success(planId.getValue(), PlanStatus.RUNNING, "计划已恢复");
        } catch (Exception e) {
            logger.error("[PlanLifecycleService] 恢复计划失败: {}", planId, e);
            return PlanOperationResult.failure(
                planId.getValue(),
                FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
                "恢复失败"
            );
        }
    }

    /**
     * 获取并验证计划（给 PlanExecutionFacade 使用）
     *
     * @param planId 计划 ID
     * @return 计划聚合根
     */
    public PlanId getAndValidatePlan(PlanId planId) {
        logger.debug("[PlanLifecycleService] 获取计划: {}", planId);
        // 这里可以添加验证逻辑，目前直接返回
        return planId;
    }
}
