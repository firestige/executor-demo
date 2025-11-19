package xyz.firestige.deploy.application.orchestration.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.DeploymentApplicationService;
import xyz.firestige.deploy.domain.plan.event.PlanResumedEvent;

/**
 * Plan 恢复事件监听器（RF-19：事件驱动架构优化）
 * <p>
 * 职责：
 * 1. 监听 PlanResumedEvent
 * 2. 委托给 DeploymentApplicationService 恢复执行 Plan
 * <p>
 * 架构设计：
 * - 监听器只做事件监听和委托，不包含业务逻辑
 * - 所有业务逻辑由 DeploymentApplicationService 统一管理
 * - 保持监听器轻量级和单一职责
 * <p>
 * 调用链：
 * PlanAggregate.resume() → PlanDomainService.resumePlan() [发布事件]
 *   → PlanResumedListener.onPlanResumed() [监听器委托]
 *   → DeploymentApplicationService.resumePlanExecution() [编排执行]
 *
 * @since RF-19 - 事件驱动架构优化
 */
@Component
public class PlanResumedListener {
    private static final Logger logger = LoggerFactory.getLogger(PlanResumedListener.class);

    private final DeploymentApplicationService deploymentApplicationService;

    public PlanResumedListener(DeploymentApplicationService deploymentApplicationService) {
        this.deploymentApplicationService = deploymentApplicationService;
    }

    /**
     * 处理 Plan 恢复事件
     * <p>
     * 事件流程：PlanAggregate.resume() → PlanDomainService.publishEvent() → 本方法 → DeploymentApplicationService
     *
     * @param event Plan 恢复事件
     */
    @EventListener
    public void onPlanResumed(PlanResumedEvent event) {
        String planId = event.getPlanId();
        logger.info("[PlanResumedListener] 收到 Plan 恢复事件: {}, 委托给 DeploymentApplicationService 执行", planId);

        try {
            // 委托给应用服务恢复执行 Plan（实际调用 TaskExecutor.retry(fromCheckpoint=true)）
            deploymentApplicationService.resumePlanExecution(planId);
        } catch (Exception e) {
            logger.error("[PlanResumedListener] 委托恢复 Plan 失败: {}", planId, e);
        }
    }
}
