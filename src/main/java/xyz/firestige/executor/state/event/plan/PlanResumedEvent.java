package xyz.firestige.executor.state.event.plan;

import xyz.firestige.executor.domain.plan.PlanStatus;

/**
 * Plan 恢复事件
 * RF-11: 当 Plan 从暂停状态恢复时产生
 */
public class PlanResumedEvent extends PlanStatusEvent {

    public PlanResumedEvent() {
        super();
    }

    public PlanResumedEvent(String planId) {
        super(planId, PlanStatus.RUNNING, "Plan 已恢复执行");
    }
}
