package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

/**
 * 任务开始执行事件
 */
public class TaskStartedEvent extends TaskStatusEvent {

    /**
     * 总的 Stage 数量
     */
    private final int totalStages;

    public TaskStartedEvent(TaskInfo info, int totalStages) {
        super(info);
        this.totalStages = totalStages;
        setMessage("任务开始执行，总 Stage 数: " + totalStages);
    }

    // Getters and Setters

    public int getTotalStages() {
        return totalStages;
    }

}

