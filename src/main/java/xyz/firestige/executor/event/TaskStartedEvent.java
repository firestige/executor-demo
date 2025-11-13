package xyz.firestige.executor.event;

/**
 * 任务已启动事件（事后）
 */
public class TaskStartedEvent extends TaskEvent {
    
    public TaskStartedEvent(String taskId) {
        super(taskId, TaskEventType.AFTER);
    }
    
    @Override
    public String getEventName() {
        return "TaskStarted";
    }
}
