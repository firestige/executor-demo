package xyz.firestige.deploy.application.projection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.plan.event.*;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjection;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjectionStore;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;

import java.util.List;

/**
 * Plan 状态投影更新器
 * 监听 Plan 相关领域事件并更新查询侧投影
 */
@Component
public class PlanStateProjectionUpdater {
    private static final Logger logger = LoggerFactory.getLogger(PlanStateProjectionUpdater.class);
    private final PlanStateProjectionStore store;

    public PlanStateProjectionUpdater(PlanStateProjectionStore store) {
        this.store = store;
    }

    @EventListener
    public void onPlanReady(PlanReadyEvent event) {
        initProjection(event.getPlanId(), event.getTaskCount(), event.getStatus().name());
    }

    @EventListener
    public void onPlanStarted(PlanStartedEvent event) {
        updateStatus(event.getPlanId(), "RUNNING");
    }

    @EventListener
    public void onPlanPaused(PlanPausedEvent event) {
        updateStatus(event.getPlanId(), "PAUSED");
    }

    @EventListener
    public void onPlanResumed(PlanResumedEvent event) {
        updateStatus(event.getPlanId(), "RUNNING");
    }

    @EventListener
    public void onPlanCompleted(PlanCompletedEvent event) {
        updateStatus(event.getPlanId(), "COMPLETED");
    }

    @EventListener
    public void onPlanFailed(PlanFailedEvent event) {
        updateStatus(event.getPlanId(), "FAILED");
    }

    private void initProjection(PlanId planId, int taskCount, String status) {
        PlanStateProjection existing = store.load(planId);
        if (existing == null) {
            PlanStateProjection projection = PlanStateProjection.builder()
                    .planId(planId)
                    .status(xyz.firestige.deploy.domain.plan.PlanStatus.valueOf(status))
                    .taskIds(List.of()) // 任务列表后续可由其他监听器补充
                    .maxConcurrency(0) // 未知并发度，后续可更新
                    .build();
            store.save(projection);
            logger.info("[PlanProjection] 初始化: planId={}, status={}", planId.getValue(), status);
        } else {
            updateStatus(planId, status);
        }
    }

    private void updateStatus(PlanId planId, String status) {
        PlanStateProjection projection = store.load(planId);
        if (projection != null) {
            projection.setStatus(xyz.firestige.deploy.domain.plan.PlanStatus.valueOf(status));
            store.save(projection);
            logger.debug("[PlanProjection] 状态更新: planId={}, status={}", planId.getValue(), status);
        }
    }
}

