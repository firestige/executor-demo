package xyz.firestige.executor.event;

/**
 * 任务创建中事件（事前）
 */
public class TaskCreatingEvent extends TaskEvent {
    
    public TaskCreatingEvent(String taskId) {
        super(taskId, TaskEventType.BEFORE);
    }
    
    @Override
    public String getEventName() {
        return "TaskCreating";
    }
}
