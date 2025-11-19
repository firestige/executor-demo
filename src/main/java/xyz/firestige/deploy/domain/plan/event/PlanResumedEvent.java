package xyz.firestige.deploy.domain.plan.event;

import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.vo.PlanId;

/**
 * Plan 恢复事件
 * RF-11: 当 Plan 从暂停状态恢复时产生
 */
public class PlanResumedEvent extends PlanStatusEvent {

    public PlanResumedEvent() {
        super();
    }

    public PlanResumedEvent(PlanId planId) {
        super(planId, PlanStatus.RUNNING, "Plan 已恢复执行");
    }
}
