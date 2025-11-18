package xyz.firestige.deploy.state.event;

import xyz.firestige.deploy.state.TaskStatus;

public class TaskRetryCompletedEvent extends TaskStatusEvent {
    private final boolean fromCheckpoint;
    public TaskRetryCompletedEvent(String taskId, boolean fromCheckpoint) {
        super(taskId, TaskStatus.RUNNING);
        this.fromCheckpoint = fromCheckpoint;
    }
    public boolean isFromCheckpoint() { return fromCheckpoint; }
}
