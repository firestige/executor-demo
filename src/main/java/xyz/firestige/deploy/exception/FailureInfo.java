package xyz.firestige.deploy.exception;

import java.time.LocalDateTime;

/**
 * 失败信息封装类
 * 统一封装任务执行过程中的失败信息
 */
public class FailureInfo {

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 错误类型
     */
    private ErrorType errorType;

    /**
     * 失败位置（Stage 名称或阶段）
     */
    private String failedAt;

    /**
     * 堆栈信息（可选）
     */
    private String stackTrace;

    /**
     * 失败时间
     */
    private LocalDateTime timestamp;

    /**
     * 是否可重试
     */
    private boolean retryable;

    public FailureInfo() {
        this.timestamp = LocalDateTime.now();
    }

    public FailureInfo(String errorCode, String errorMessage, ErrorType errorType) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
        this.timestamp = LocalDateTime.now();
    }

    public FailureInfo(String errorCode, String errorMessage, ErrorType errorType, String failedAt, boolean retryable) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
        this.failedAt = failedAt;
        this.retryable = retryable;
        this.timestamp = LocalDateTime.now();
    }

    public static FailureInfo of(ErrorType errorType, String errorMessage) {
        return new FailureInfo(errorType.name(), errorMessage, errorType);
    }

    public static FailureInfo of(ErrorType errorType, String errorMessage, String failedAt) {
        FailureInfo info = new FailureInfo(errorType.name(), errorMessage, errorType);
        info.setFailedAt(failedAt);
        return info;
    }

    public static FailureInfo of(ErrorType errorType, String errorMessage, boolean retryable) {
        FailureInfo info = new FailureInfo(errorType.name(), errorMessage, errorType);
        info.setRetryable(retryable);
        return info;
    }

    public static FailureInfo fromException(Exception e, ErrorType errorType, String failedAt) {
        FailureInfo info = new FailureInfo();
        info.setErrorCode(errorType.name());
        info.setErrorMessage(e.getMessage());
        info.setErrorType(errorType);
        info.setFailedAt(failedAt);
        info.setStackTrace(getStackTraceAsString(e));
        info.setRetryable(isRetryableException(e));
        return info;
    }

    private static String getStackTraceAsString(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    private static boolean isRetryableException(Exception e) {
        // 判断异常是否可重试
        // 例如：网络异常、超时异常等通常可重试
        String className = e.getClass().getName();
        return className.contains("Timeout") ||
               className.contains("Network") ||
               className.contains("Connection");
    }

    // Getters and Setters

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public void setErrorType(ErrorType errorType) {
        this.errorType = errorType;
    }

    public String getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(String failedAt) {
        this.failedAt = failedAt;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public void setRetryable(boolean retryable) {
        this.retryable = retryable;
    }

    @Override
    public String toString() {
        return "FailureInfo{" +
                "errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", errorType=" + errorType +
                ", failedAt='" + failedAt + '\'' +
                ", timestamp=" + timestamp +
                ", retryable=" + retryable +
                '}';
    }
}

