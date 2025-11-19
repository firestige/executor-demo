package xyz.firestige.deploy.domain.plan.event;

import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.vo.PlanId;

/**
 * Plan 失败事件
 * RF-11: 当 Plan 执行失败时产生
 */
public class PlanFailedEvent extends PlanStatusEvent {

    private String failureSummary;

    public PlanFailedEvent() {
        super();
    }

    public PlanFailedEvent(PlanId planId, String failureSummary) {
        super(planId, PlanStatus.FAILED, "Plan 执行失败: " + failureSummary);
        this.failureSummary = failureSummary;
    }

    public String getFailureSummary() {
        return failureSummary;
    }

    public void setFailureSummary(String failureSummary) {
        this.failureSummary = failureSummary;
    }
}
