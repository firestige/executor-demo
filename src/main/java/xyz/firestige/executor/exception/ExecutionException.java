package xyz.firestige.executor.exception;

/**
 * 执行异常
 * 任务执行过程中发生错误时抛出
 */
public class ExecutionException extends ExecutorException {

    public ExecutionException(String message) {
        super(message);
        setErrorType(ErrorType.SYSTEM_ERROR);
    }

    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
        setErrorType(ErrorType.SYSTEM_ERROR);
    }

    public ExecutionException(String errorCode, String message, ErrorType errorType) {
        super(errorCode, message, errorType);
    }

    public ExecutionException(String errorCode, String message, ErrorType errorType, Throwable cause) {
        super(errorCode, message, errorType, cause);
    }

    public ExecutionException(FailureInfo failureInfo) {
        super(failureInfo);
    }

    public ExecutionException(FailureInfo failureInfo, Throwable cause) {
        super(failureInfo, cause);
    }
}

