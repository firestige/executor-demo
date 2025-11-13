package xyz.firestige.executor.exception;

/**
 * 任务异常基类
 * 所有任务相关异常的父类
 */
public class TaskException extends RuntimeException {
    
    private final String taskId;
    
    public TaskException(String message) {
        super(message);
        this.taskId = null;
    }
    
    public TaskException(String message, Throwable cause) {
        super(message, cause);
        this.taskId = null;
    }
    
    public TaskException(String taskId, String message) {
        super(message);
        this.taskId = taskId;
    }
    
    public TaskException(String taskId, String message, Throwable cause) {
        super(message, cause);
        this.taskId = taskId;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    @Override
    public String getMessage() {
        if (taskId != null) {
            return String.format("[TaskId: %s] %s", taskId, super.getMessage());
        }
        return super.getMessage();
    }
}
