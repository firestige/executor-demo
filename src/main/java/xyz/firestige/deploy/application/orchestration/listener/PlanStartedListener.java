package xyz.firestige.deploy.application.orchestration.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import xyz.firestige.deploy.application.DeploymentApplicationService;
import xyz.firestige.deploy.domain.plan.event.PlanStartedEvent;

/**
 * Plan 启动事件监听器（精简版）
 * <p>
 * 职责：
 * 1. 监听 PlanStartedEvent
 * 2. 委托给 DeploymentApplicationService 执行 Plan
 * <p>
 * 架构设计：
 * - 监听器只做事件监听和委托，不包含业务逻辑
 * - 所有业务逻辑由 DeploymentApplicationService 统一管理
 * - 保持监听器轻量级和单一职责
 *
 * @since Phase 19 - 架构简化
 */
@Component
public class PlanStartedListener {
    private static final Logger logger = LoggerFactory.getLogger(PlanStartedListener.class);

    private final DeploymentApplicationService deploymentApplicationService;

    public PlanStartedListener(DeploymentApplicationService deploymentApplicationService) {
        this.deploymentApplicationService = deploymentApplicationService;
    }

    /**
     * 处理 Plan 启动事件
     * <p>
     * 事件流程：PlanAggregate.start() → PlanDomainService.publishEvent() → 本方法 → DeploymentApplicationService
     *
     * @param event Plan 启动事件
     */
    @EventListener
    public void onPlanStarted(PlanStartedEvent event) {
        String planId = event.getPlanId();
        logger.info("[PlanStartedListener] 收到 Plan 启动事件: {}, 委托给 DeploymentApplicationService 执行", planId);

        try {
            // 委托给应用服务执行 Plan
            deploymentApplicationService.executePlan(planId);
        } catch (Exception e) {
            logger.error("[PlanStartedListener] 委托执行 Plan 失败: {}", planId, e);
        }
    }
}
