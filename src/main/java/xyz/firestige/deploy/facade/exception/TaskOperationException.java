package xyz.firestige.deploy.facade.exception;

import xyz.firestige.deploy.domain.shared.exception.FailureInfo;

/**
 * 任务操作异常
 * 当任务操作（暂停/恢复/回滚/重试/取消）失败时抛出
 */
public class TaskOperationException extends RuntimeException {

    private final FailureInfo failureInfo;

    public TaskOperationException(String message, FailureInfo failureInfo) {
        super(message);
        this.failureInfo = failureInfo;
    }

    public TaskOperationException(String message, FailureInfo failureInfo, Throwable cause) {
        super(message, cause);
        this.failureInfo = failureInfo;
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }
}

