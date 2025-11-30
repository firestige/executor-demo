package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;

/**
 * 任务取消事件
 * <p>
 * T-036: 重构为使用三大视图
 */
public class TaskCancelledEvent extends TaskStatusEvent {

    private final TaskInfoView taskInfo;
    private final String cancelledBy;
    private final String lastStage;

    public TaskCancelledEvent(TaskAggregate task, String cancelledBy, String lastStage) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.cancelledBy = cancelledBy;
        this.lastStage = lastStage;
        setMessage("任务已取消，取消者: " + cancelledBy + ", 最后执行的 Stage: " + lastStage);
    }

    @Deprecated
    public TaskCancelledEvent(TaskInfo info, String cancelledBy, String lastStage) {
        super(info);
        this.taskInfo = getTaskInfo();
        this.cancelledBy = cancelledBy;
        this.lastStage = lastStage;
        setMessage("任务已取消，取消者: " + cancelledBy + ", 最后执行的 Stage: " + lastStage);
    }

    public String getCancelledBy() { return cancelledBy; }
    public String getLastStage() { return lastStage; }
    public TaskInfoView getTaskInfoView() { return taskInfo; }
}
