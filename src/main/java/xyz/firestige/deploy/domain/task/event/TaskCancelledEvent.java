package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

/**
 * 任务取消事件
 */
public class TaskCancelledEvent extends TaskStatusEvent {

    private final String cancelledBy;
    private final String lastStage;

    public TaskCancelledEvent(TaskInfo info, String cancelledBy, String lastStage) {
        super(info);
        this.cancelledBy = cancelledBy;
        this.lastStage = lastStage;
        setMessage("任务已取消，取消者: " + cancelledBy + ", 最后执行的 Stage: " + lastStage);
    }

    public String getCancelledBy() { return cancelledBy; }
    public String getLastStage() { return lastStage; }
}
