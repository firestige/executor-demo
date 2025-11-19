package xyz.firestige.deploy.application.orchestration.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import xyz.firestige.deploy.domain.plan.event.PlanPausedEvent;

/**
 * Plan 暂停事件监听器（RF-19：事件驱动架构优化）
 * <p>
 * 职责：
 * 1. 监听 PlanPausedEvent
 * 2. 记录日志和审计信息
 * 3. 可选：发送通知、更新监控指标等
 * <p>
 * 架构设计：
 * - 暂停是协商式操作，TaskExecutor 会自行检查 pauseRequested 标志并停止
 * - 监听器不需要调用 DeploymentApplicationService 进行任务编排
 * - 只做通知和日志记录，保持轻量级
 * <p>
 * 调用链：
 * PlanAggregate.pause() → PlanDomainService.pausePlan() [发布事件]
 *   → PlanPausedListener.onPlanPaused() [日志记录]
 *   → TaskAggregate.pause() [设置 pauseRequested 标志]
 *   → TaskExecutor 主循环检查标志并自行停止
 *
 * @since RF-19 - 事件驱动架构优化
 */
@Component
public class PlanPausedListener {
    private static final Logger logger = LoggerFactory.getLogger(PlanPausedListener.class);

    /**
     * 处理 Plan 暂停事件
     * <p>
     * 事件流程：PlanAggregate.pause() → PlanDomainService.publishEvent() → 本方法
     * <p>
     * 注意：不需要调用 DeploymentApplicationService，因为：
     * 1. TaskExecutor 会自己检查 Task.pauseRequested 标志
     * 2. 暂停是协商式操作，不需要主动编排
     *
     * @param event Plan 暂停事件
     */
    @EventListener
    public void onPlanPaused(PlanPausedEvent event) {
        String planId = event.getPlanId();
        logger.info("[PlanPausedListener] Plan {} 已暂停，TaskExecutor 将自动检查标志并停止", planId);

        // 可选：发送通知、更新监控指标、记录审计日志等
        // notificationService.sendPlanPausedNotification(planId);
        // metricsService.recordPlanPaused(planId);
        // auditService.logPlanPaused(planId);
    }
}
