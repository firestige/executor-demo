package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

/**
 * 任务暂停事件
 */
public class TaskPausedEvent extends TaskStatusEvent {

    /**
     * 操作人
     */
    private final String pausedBy;

    /**
     * 暂停时的 Stage
     */
    private final String currentStage;

    public TaskPausedEvent(TaskInfo info, String pausedBy, String currentStage) {
        super(info);
        this.pausedBy = pausedBy;
        this.currentStage = currentStage;
        setMessage("任务已暂停，当前 Stage: " + currentStage);
    }

    // Getters and Setters

    public String getPausedBy() {
        return pausedBy;
    }

    public String getCurrentStage() {
        return currentStage;
    }
}

