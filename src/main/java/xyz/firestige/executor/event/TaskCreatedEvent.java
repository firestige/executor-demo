package xyz.firestige.executor.event;

/**
 * 任务已创建事件（事后）
 */
public class TaskCreatedEvent extends TaskEvent {
    
    public TaskCreatedEvent(String taskId) {
        super(taskId, TaskEventType.AFTER);
    }
    
    @Override
    public String getEventName() {
        return "TaskCreated";
    }
}
