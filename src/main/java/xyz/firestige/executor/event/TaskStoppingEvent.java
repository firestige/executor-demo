package xyz.firestige.executor.event;

/**
 * 任务停止中事件（事前）
 */
public class TaskStoppingEvent extends TaskEvent {
    
    public TaskStoppingEvent(String taskId) {
        super(taskId, TaskEventType.BEFORE);
    }
    
    @Override
    public String getEventName() {
        return "TaskStopping";
    }
}
