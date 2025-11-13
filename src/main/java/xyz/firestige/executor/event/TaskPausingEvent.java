package xyz.firestige.executor.event;

/**
 * 任务暂停中事件（事前）
 */
public class TaskPausingEvent extends TaskEvent {
    
    public TaskPausingEvent(String taskId) {
        super(taskId, TaskEventType.BEFORE);
    }
    
    @Override
    public String getEventName() {
        return "TaskPausing";
    }
}
