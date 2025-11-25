package xyz.firestige.deploy.domain.plan.event;

import xyz.firestige.deploy.domain.plan.PlanInfo;
import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.vo.PlanId;

/**
 * Plan 启动事件
 * RF-11: 当 Plan 开始执行时产生
 */
public class PlanStartedEvent extends PlanStatusEvent {

    private int taskCount;

    public PlanStartedEvent(PlanInfo info, int taskCount) {
        super(info, String.format("Plan 已启动，开始执行 %d 个任务", taskCount));
        this.taskCount = taskCount;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public void setTaskCount(int taskCount) {
        this.taskCount = taskCount;
    }
}
