package xyz.firestige.executor.event;

/**
 * 任务已暂停事件（事后）
 */
public class TaskPausedEvent extends TaskEvent {
    
    public TaskPausedEvent(String taskId) {
        super(taskId, TaskEventType.AFTER);
    }
    
    @Override
    public String getEventName() {
        return "TaskPaused";
    }
}
