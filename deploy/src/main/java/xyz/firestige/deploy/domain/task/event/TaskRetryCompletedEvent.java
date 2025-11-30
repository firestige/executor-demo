package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;
import xyz.firestige.deploy.domain.task.TaskProgressView;

/**
 * 任务重试完成事件
 * <p>
 * T-036: 重构为使用三大视图
 */
public class TaskRetryCompletedEvent extends TaskStatusEvent {

    private final TaskInfoView taskInfo;
    private final TaskProgressView progress;
    private final boolean fromCheckpoint;

    public TaskRetryCompletedEvent(TaskAggregate task, boolean fromCheckpoint) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.progress = task.getProgressView();
        this.fromCheckpoint = fromCheckpoint;
        setMessage("任务重试完成");
    }

    @Deprecated
    public TaskRetryCompletedEvent(TaskInfo info, boolean fromCheckpoint) {
        super(info);
        this.taskInfo = getTaskInfo();
        this.progress = null;
        this.fromCheckpoint = fromCheckpoint;
        setMessage("任务重试完成");
    }

    public boolean isFromCheckpoint() { return fromCheckpoint; }
    public TaskInfoView getTaskInfoView() { return taskInfo; }
    public TaskProgressView getProgress() { return progress; }
}
