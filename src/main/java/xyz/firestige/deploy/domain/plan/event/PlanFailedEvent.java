package xyz.firestige.deploy.domain.plan.event;

import xyz.firestige.deploy.domain.plan.PlanStatus;

/**
 * Plan 失败事件
 * RF-11: 当 Plan 执行失败时产生
 */
public class PlanFailedEvent extends PlanStatusEvent {

    private String failureSummary;

    public PlanFailedEvent() {
        super();
    }

    public PlanFailedEvent(String planId, String failureSummary) {
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
