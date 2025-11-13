package xyz.firestige.executor.event;

/**
 * 任务回滚中事件
 */
public class TaskRollingBackEvent extends TaskEvent {
    
    public TaskRollingBackEvent(String taskId) {
        super(taskId, TaskEventType.BEFORE);
    }
    
    @Override
    public String getEventName() {
        return "TaskRollingBack";
    }
}
