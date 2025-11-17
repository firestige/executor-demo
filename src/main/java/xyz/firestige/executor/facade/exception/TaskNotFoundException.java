package xyz.firestige.executor.facade.exception;

/**
 * 任务未找到异常
 * 当根据 taskId 或 tenantId 查询任务时，任务不存在则抛出此异常
 */
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(String message) {
        super(message);
    }

    public TaskNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

