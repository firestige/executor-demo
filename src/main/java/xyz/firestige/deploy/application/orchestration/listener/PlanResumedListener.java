package xyz.firestige.deploy.application.orchestration.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import xyz.firestige.deploy.application.facade.PlanExecutionFacade;
import xyz.firestige.deploy.domain.plan.event.PlanResumedEvent;

/**
 * Plan 恢复事件监听器（RF-20：服务拆分优化）
 * <p>
 * 职责：
 * 1. 监听 PlanResumedEvent
 * 2. 委托给 PlanExecutionFacade 恢复执行 Plan
 * <p>
 * 架构设计：
 * - 监听器只做事件监听和委托，不包含业务逻辑
 * - 使用 PlanExecutionFacade 简化依赖注入
 * - 保持监听器轻量级和单一职责
 * <p>
 * 调用链：
 * PlanAggregate.resume() → PlanDomainService.resumePlan() [发布事件]
 *   → PlanResumedListener.onPlanResumed() [监听器委托]
 *   → PlanExecutionFacade.resumePlanExecution() [编排执行]
 *
 * @since RF-20 - 服务拆分
 */
@Component
public class PlanResumedListener {
    private static final Logger logger = LoggerFactory.getLogger(PlanResumedListener.class);

    private final PlanExecutionFacade planExecutionFacade;

    public PlanResumedListener(PlanExecutionFacade planExecutionFacade) {
        this.planExecutionFacade = planExecutionFacade;
    }

    /**
     * 处理 Plan 恢复事件
     * <p>
     * 事件流程：PlanAggregate.resume() → PlanDomainService.publishEvent() → 本方法 → PlanExecutionFacade
     *
     * @param event Plan 恢复事件
     */
    @EventListener
    public void onPlanResumed(PlanResumedEvent event) {
        String planId = event.getPlanId();
        logger.info("[PlanResumedListener] 收到 Plan 恢复事件: {}, 委托给 PlanExecutionFacade 执行", planId);

        try {
            // 委托给执行门面恢复执行 Plan（实际调用 TaskExecutor.retry(fromCheckpoint=true)）
            planExecutionFacade.resumePlanExecution(planId);
        } catch (Exception e) {
            logger.error("[PlanResumedListener] 委托恢复 Plan 失败: {}", planId, e);
        }
    }
}
