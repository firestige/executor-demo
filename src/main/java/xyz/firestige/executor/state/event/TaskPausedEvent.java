package xyz.firestige.executor.state.event;

import xyz.firestige.executor.state.TaskStatus;

/**
 * 任务暂停事件
 */
public class TaskPausedEvent extends TaskStatusEvent {

    /**
     * 操作人
     */
    private String pausedBy;

    /**
     * 暂停时的 Stage
     */
    private String currentStage;

    public TaskPausedEvent() {
        super();
        setStatus(TaskStatus.PAUSED);
    }

    public TaskPausedEvent(String taskId, String pausedBy, String currentStage) {
        super(taskId, TaskStatus.PAUSED);
        this.pausedBy = pausedBy;
        this.currentStage = currentStage;
        setMessage("任务已暂停，当前 Stage: " + currentStage);
    }

    // Getters and Setters

    public String getPausedBy() {
        return pausedBy;
    }

    public void setPausedBy(String pausedBy) {
        this.pausedBy = pausedBy;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }
}

