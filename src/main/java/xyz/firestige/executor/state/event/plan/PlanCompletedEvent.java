package xyz.firestige.executor.state.event.plan;

import xyz.firestige.executor.domain.plan.PlanStatus;

/**
 * Plan 完成事件
 * RF-11: 当 Plan 成功完成所有任务时产生
 */
public class PlanCompletedEvent extends PlanStatusEvent {

    private int taskCount;

    public PlanCompletedEvent() {
        super();
    }

    public PlanCompletedEvent(String planId, int taskCount) {
        super(planId, PlanStatus.COMPLETED, String.format("Plan 已完成，成功执行 %d 个任务", taskCount));
        this.taskCount = taskCount;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public void setTaskCount(int taskCount) {
        this.taskCount = taskCount;
    }
}
