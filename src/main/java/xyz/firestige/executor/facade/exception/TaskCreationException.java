package xyz.firestige.executor.facade.exception;

import xyz.firestige.executor.exception.FailureInfo;

/**
 * 任务创建异常
 * 当任务创建失败时抛出
 */
public class TaskCreationException extends RuntimeException {

    private final FailureInfo failureInfo;

    public TaskCreationException(String message, FailureInfo failureInfo) {
        super(message);
        this.failureInfo = failureInfo;
    }

    public TaskCreationException(String message, FailureInfo failureInfo, Throwable cause) {
        super(message, cause);
        this.failureInfo = failureInfo;
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }
}

