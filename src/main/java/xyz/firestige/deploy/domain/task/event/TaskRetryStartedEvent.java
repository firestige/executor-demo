package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskStatus;

public class TaskRetryStartedEvent extends TaskStatusEvent {
    private final boolean fromCheckpoint;
    public TaskRetryStartedEvent(String taskId, boolean fromCheckpoint) {
        super(taskId, TaskStatus.RUNNING);
        this.fromCheckpoint = fromCheckpoint;
    }
    public boolean isFromCheckpoint() { return fromCheckpoint; }
}
