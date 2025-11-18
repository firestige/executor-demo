package xyz.firestige.executor.state.event.plan;

import xyz.firestige.executor.domain.plan.PlanStatus;

/**
 * Plan 暂停事件
 * RF-11: 当 Plan 暂停执行时产生
 */
public class PlanPausedEvent extends PlanStatusEvent {

    public PlanPausedEvent() {
        super();
    }

    public PlanPausedEvent(String planId) {
        super(planId, PlanStatus.PAUSED, "Plan 已暂停");
    }
}
