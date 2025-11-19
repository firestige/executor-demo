package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.TaskStatus;

public class TaskRetryCompletedEvent extends TaskStatusEvent {
    private final boolean fromCheckpoint;
    public TaskRetryCompletedEvent(TaskId taskId, boolean fromCheckpoint) {
        super(taskId, TaskStatus.RUNNING);
        this.fromCheckpoint = fromCheckpoint;
    }
    public boolean isFromCheckpoint() { return fromCheckpoint; }
}
