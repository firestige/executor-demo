package xyz.firestige.executor.event;

/**
 * 任务回滚完成事件
 */
public class TaskRollbackCompleteEvent extends TaskEvent {
    
    public TaskRollbackCompleteEvent(String taskId) {
        super(taskId, TaskEventType.AFTER);
    }
    
    @Override
    public String getEventName() {
        return "TaskRollbackComplete";
    }
}
