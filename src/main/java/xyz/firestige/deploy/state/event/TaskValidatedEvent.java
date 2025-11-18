package xyz.firestige.deploy.state.event;

import xyz.firestige.deploy.state.TaskStatus;

/**
 * 任务校验通过事件
 */
public class TaskValidatedEvent extends TaskStatusEvent {

    /**
     * 已校验的配置数量
     */
    private int validatedCount;

    public TaskValidatedEvent() {
        super();
        setStatus(TaskStatus.PENDING);
    }

    public TaskValidatedEvent(String taskId, int validatedCount) {
        super(taskId, TaskStatus.PENDING);
        this.validatedCount = validatedCount;
        setMessage("任务校验通过，有效配置数量: " + validatedCount);
    }

    // Getters and Setters

    public int getValidatedCount() {
        return validatedCount;
    }

    public void setValidatedCount(int validatedCount) {
        this.validatedCount = validatedCount;
    }
}

