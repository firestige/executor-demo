package xyz.firestige.deploy.domain.plan.event;

import xyz.firestige.deploy.domain.plan.PlanStatus;

/**
 * Plan 准备就绪事件
 * RF-11: 当 Plan 标记为 READY 状态时产生
 */
public class PlanReadyEvent extends PlanStatusEvent {

    private int taskCount;

    public PlanReadyEvent() {
        super();
    }

    public PlanReadyEvent(String planId, int taskCount) {
        super(planId, PlanStatus.READY, String.format("Plan 已准备就绪，包含 %d 个任务", taskCount));
        this.taskCount = taskCount;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public void setTaskCount(int taskCount) {
        this.taskCount = taskCount;
    }
}
