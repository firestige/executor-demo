package xyz.firestige.executor.event;

/**
 * 任务已停止事件（事后）
 */
public class TaskStoppedEvent extends TaskEvent {
    
    public TaskStoppedEvent(String taskId) {
        super(taskId, TaskEventType.AFTER);
    }
    
    @Override
    public String getEventName() {
        return "TaskStopped";
    }
}
