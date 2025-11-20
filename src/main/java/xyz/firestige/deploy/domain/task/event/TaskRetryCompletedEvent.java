package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

public class TaskRetryCompletedEvent extends TaskStatusEvent {
    private final boolean fromCheckpoint;
    public TaskRetryCompletedEvent(TaskInfo info, boolean fromCheckpoint) {
        super(info);
        this.fromCheckpoint = fromCheckpoint;
    }
    public boolean isFromCheckpoint() { return fromCheckpoint; }
}
