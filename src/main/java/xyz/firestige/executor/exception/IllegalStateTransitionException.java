package xyz.firestige.executor.exception;

import xyz.firestige.executor.domain.TaskStatus;

/**
 * 非法状态转换异常
 * 当尝试执行不允许的状态转换时抛出
 */
public class IllegalStateTransitionException extends TaskException {
    
    private final TaskStatus fromStatus;
    private final TaskStatus toStatus;
    
    public IllegalStateTransitionException(TaskStatus fromStatus, TaskStatus toStatus) {
        super(String.format("Illegal state transition from %s to %s", fromStatus, toStatus));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }
    
    public IllegalStateTransitionException(String taskId, TaskStatus fromStatus, TaskStatus toStatus) {
        super(taskId, String.format("Illegal state transition from %s to %s", fromStatus, toStatus));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }
    
    public TaskStatus getFromStatus() {
        return fromStatus;
    }
    
    public TaskStatus getToStatus() {
        return toStatus;
    }
}
