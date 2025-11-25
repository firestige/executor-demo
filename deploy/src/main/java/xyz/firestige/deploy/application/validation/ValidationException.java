package xyz.firestige.deploy.application.validation;

import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.exception.ExecutorException;

/**
 * 校验异常
 * 配置数据校验失败时抛出
 */
public class ValidationException extends ExecutorException {

    public ValidationException(String message) {
        super(message);
        setErrorType(ErrorType.VALIDATION_ERROR);
        setRetryable(false); // 校验错误通常不可重试
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        setErrorType(ErrorType.VALIDATION_ERROR);
        setRetryable(false);
    }

    public ValidationException(String errorCode, String message) {
        super(errorCode, message, ErrorType.VALIDATION_ERROR);
        setRetryable(false);
    }

    public ValidationException(FailureInfo failureInfo) {
        super(failureInfo);
    }
}

