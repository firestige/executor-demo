package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

/**
 * 任务校验通过事件
 */
public class TaskValidatedEvent extends TaskStatusEvent {

    /**
     * 已校验的配置数量
     */
    private final int validatedCount;

    public TaskValidatedEvent(TaskInfo info, int validatedCount) {
        super(info);
        this.validatedCount = validatedCount;
        setMessage("任务校验通过，有效配置数量: " + validatedCount);
    }

    // Getters and Setters

    public int getValidatedCount() {
        return validatedCount;
    }
}

