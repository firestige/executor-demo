package xyz.firestige.executor.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * 执行器基础异常类
 * 所有执行器相关异常的基类
 */
public class ExecutorException extends RuntimeException {

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误类型
     */
    private ErrorType errorType;

    /**
     * 是否可重试
     */
    private boolean retryable;

    /**
     * 上下文信息
     */
    private Map<String, Object> context;

    /**
     * 失败信息
     */
    private FailureInfo failureInfo;

    public ExecutorException(String message) {
        super(message);
        this.errorType = ErrorType.SYSTEM_ERROR;
        this.context = new HashMap<>();
    }

    public ExecutorException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = ErrorType.SYSTEM_ERROR;
        this.context = new HashMap<>();
    }

    public ExecutorException(String errorCode, String message, ErrorType errorType) {
        super(message);
        this.errorCode = errorCode;
        this.errorType = errorType;
        this.context = new HashMap<>();
    }

    public ExecutorException(String errorCode, String message, ErrorType errorType, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorType = errorType;
        this.context = new HashMap<>();
    }

    public ExecutorException(FailureInfo failureInfo) {
        super(failureInfo.getErrorMessage());
        this.failureInfo = failureInfo;
        this.errorCode = failureInfo.getErrorCode();
        this.errorType = failureInfo.getErrorType();
        this.retryable = failureInfo.isRetryable();
        this.context = new HashMap<>();
    }

    public ExecutorException(FailureInfo failureInfo, Throwable cause) {
        super(failureInfo.getErrorMessage(), cause);
        this.failureInfo = failureInfo;
        this.errorCode = failureInfo.getErrorCode();
        this.errorType = failureInfo.getErrorType();
        this.retryable = failureInfo.isRetryable();
        this.context = new HashMap<>();
    }

    /**
     * 添加上下文信息
     */
    public ExecutorException addContext(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    /**
     * 设置是否可重试
     */
    public ExecutorException setRetryable(boolean retryable) {
        this.retryable = retryable;
        return this;
    }

    /**
     * 转换为 FailureInfo
     */
    public FailureInfo toFailureInfo() {
        if (this.failureInfo != null) {
            return this.failureInfo;
        }

        FailureInfo info = new FailureInfo();
        info.setErrorCode(this.errorCode != null ? this.errorCode : this.errorType.name());
        info.setErrorMessage(this.getMessage());
        info.setErrorType(this.errorType);
        info.setRetryable(this.retryable);

        if (this.getCause() != null) {
            info.setStackTrace(getStackTraceAsString(this));
        }

        return info;
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        if (throwable.getCause() != null) {
            sb.append("Caused by: ").append(getStackTraceAsString(throwable.getCause()));
        }
        return sb.toString();
    }

    // Getters and Setters

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public void setErrorType(ErrorType errorType) {
        this.errorType = errorType;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }

    public void setFailureInfo(FailureInfo failureInfo) {
        this.failureInfo = failureInfo;
    }
}

