package xyz.firestige.deploy.infrastructure.state.event;

import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * 任务开始执行事件
 */
public class TaskStartedEvent extends TaskStatusEvent {

    /**
     * 总的 Stage 数量
     */
    private int totalStages;

    public TaskStartedEvent() {
        super();
        setStatus(TaskStatus.RUNNING);
    }

    public TaskStartedEvent(String taskId, int totalStages) {
        super(taskId, TaskStatus.RUNNING);
        this.totalStages = totalStages;
        setMessage("任务开始执行，总 Stage 数: " + totalStages);
    }

    public TaskStartedEvent(String taskId, String tenantId, Long planId, int totalStages) {
        super(taskId, tenantId, planId, TaskStatus.RUNNING);
        this.totalStages = totalStages;
        setMessage("任务开始执行，总 Stage 数: " + totalStages);
    }

    // Getters and Setters

    public int getTotalStages() {
        return totalStages;
    }

    public void setTotalStages(int totalStages) {
        this.totalStages = totalStages;
    }
}

