package xyz.firestige.deploy.domain.plan.event;

import xyz.firestige.deploy.domain.plan.PlanStatus;

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
