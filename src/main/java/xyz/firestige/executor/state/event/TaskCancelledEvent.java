package xyz.firestige.executor.state.event;

import xyz.firestige.executor.state.TaskStatus;

/**
 * 任务取消事件
 */
public class TaskCancelledEvent extends TaskStatusEvent {

    public TaskCancelledEvent() {
        super();
        setStatus(TaskStatus.CANCELLED);
        setMessage("任务已取消");
    }

    public TaskCancelledEvent(String taskId) {
        super(taskId, TaskStatus.CANCELLED);
        setMessage("任务已取消");
    }

    public TaskCancelledEvent(String taskId, String tenantId, Long planId) {
        super(taskId, tenantId, planId, TaskStatus.CANCELLED);
        setMessage("任务已取消");
    }
}

