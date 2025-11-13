package xyz.firestige.executor.event;

/**
 * 任务已失败事件（事后）
 */
public class TaskFailedEvent extends TaskEvent {
    
    public TaskFailedEvent(String taskId) {
        super(taskId, TaskEventType.AFTER);
    }
    
    @Override
    public String getEventName() {
        return "TaskFailed";
    }
}
