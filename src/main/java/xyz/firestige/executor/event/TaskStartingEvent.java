package xyz.firestige.executor.event;

/**
 * 任务启动中事件（事前）
 */
public class TaskStartingEvent extends TaskEvent {
    
    public TaskStartingEvent(String taskId) {
        super(taskId, TaskEventType.BEFORE);
    }
    
    @Override
    public String getEventName() {
        return "TaskStarting";
    }
}
