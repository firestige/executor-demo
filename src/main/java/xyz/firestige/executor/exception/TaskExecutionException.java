package xyz.firestige.executor.exception;

/**
 * 任务执行异常
 * 任务执行过程中发生的通用异常
 */
public class TaskExecutionException extends TaskException {
    
    public TaskExecutionException(String message) {
        super(message);
    }
    
    public TaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public TaskExecutionException(String taskId, String message) {
        super(taskId, message);
    }
    
    public TaskExecutionException(String taskId, String message, Throwable cause) {
        super(taskId, message, cause);
    }
}
