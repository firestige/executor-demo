package xyz.firestige.executor.event;

/**
 * 任务完成中事件（事前）
 */
public class TaskCompletingEvent extends TaskEvent {
    
    public TaskCompletingEvent(String taskId) {
        super(taskId, TaskEventType.BEFORE);
    }
    
    @Override
    public String getEventName() {
        return "TaskCompleting";
    }
}
