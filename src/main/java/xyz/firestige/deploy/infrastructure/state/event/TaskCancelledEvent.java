package xyz.firestige.deploy.infrastructure.state.event;

import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * 任务取消事件
 */
public class TaskCancelledEvent extends TaskStatusEvent {

    private String cancelledBy;
    private String lastStage;

    public TaskCancelledEvent() {
        super();
        setStatus(TaskStatus.CANCELLED);
        setMessage("任务已取消");
    }

    public TaskCancelledEvent(String taskId) {
        super(taskId, TaskStatus.CANCELLED);
        setMessage("任务已取消");
    }

    public TaskCancelledEvent(String taskId, String tenantId, Long planId) {
        super(taskId, tenantId, planId, TaskStatus.CANCELLED);
        setMessage("任务已取消");
    }

    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public String getLastStage() { return lastStage; }
    public void setLastStage(String lastStage) { this.lastStage = lastStage; }
}
