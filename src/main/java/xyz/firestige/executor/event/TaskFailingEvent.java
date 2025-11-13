package xyz.firestige.executor.event;

/**
 * 任务失败中事件（事前）
 */
public class TaskFailingEvent extends TaskEvent {
    
    public TaskFailingEvent(String taskId) {
        super(taskId, TaskEventType.BEFORE);
    }
    
    @Override
    public String getEventName() {
        return "TaskFailing";
    }
}
