package xyz.firestige.deploy.application.orchestration.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import xyz.firestige.deploy.application.facade.PlanExecutionFacade;
import xyz.firestige.deploy.domain.plan.event.PlanStartedEvent;

/**
 * Plan 启动事件监听器（RF-20：服务拆分优化）
 * <p>
 * 职责：
 * 1. 监听 PlanStartedEvent
 * 2. 委托给 PlanExecutionFacade 执行 Plan
 * <p>
 * 架构设计：
 * - 监听器只做事件监听和委托，不包含业务逻辑
 * - 使用 PlanExecutionFacade 简化依赖注入
 * - 保持监听器轻量级和单一职责
 *
 * @since RF-20 - 服务拆分
 */
@Component
public class PlanStartedListener {
    private static final Logger logger = LoggerFactory.getLogger(PlanStartedListener.class);

    private final PlanExecutionFacade planExecutionFacade;

    public PlanStartedListener(PlanExecutionFacade planExecutionFacade) {
        this.planExecutionFacade = planExecutionFacade;
    }

    /**
     * 处理 Plan 启动事件
     * <p>
     * 事件流程：PlanAggregate.start() → PlanDomainService.publishEvent() → 本方法 → PlanExecutionFacade
     *
     * @param event Plan 启动事件
     */
    @EventListener
    public void onPlanStarted(PlanStartedEvent event) {
        String planId = event.getPlanId();
        logger.info("[PlanStartedListener] 收到 Plan 启动事件: {}, 委托给 PlanExecutionFacade 执行", planId);

        try {
            // 委托给执行门面执行 Plan
            planExecutionFacade.executePlan(planId);
        } catch (Exception e) {
            logger.error("[PlanStartedListener] 委托执行 Plan 失败: {}", planId, e);
        }
    }
}
