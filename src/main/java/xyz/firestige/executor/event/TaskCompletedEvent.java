package xyz.firestige.executor.event;

/**
 * 任务已完成事件（事后）
 */
public class TaskCompletedEvent extends TaskEvent {
    
    public TaskCompletedEvent(String taskId) {
        super(taskId, TaskEventType.AFTER);
    }
    
    @Override
    public String getEventName() {
        return "TaskCompleted";
    }
}
