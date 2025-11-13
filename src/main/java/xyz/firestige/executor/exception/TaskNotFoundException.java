package xyz.firestige.executor.exception;

/**
 * 任务未找到异常
 * 当根据taskId查找任务但未找到时抛出
 */
public class TaskNotFoundException extends TaskException {
    
    public TaskNotFoundException(String taskId) {
        super(taskId, String.format("Task not found: %s", taskId));
    }
    
    public TaskNotFoundException(String taskId, String message) {
        super(taskId, message);
    }
}
