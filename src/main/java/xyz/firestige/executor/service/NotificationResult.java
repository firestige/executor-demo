package xyz.firestige.executor.service;

import xyz.firestige.executor.exception.FailureInfo;

import java.time.LocalDateTime;

/**
 * 通知结果类
 * 封装服务通知的执行结果
 */
public class NotificationResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 失败信息（如果失败）
     */
    private FailureInfo failureInfo;

    /**
     * 响应数据
     */
    private Object responseData;

    /**
     * 通知时间
     */
    private LocalDateTime timestamp;

    public NotificationResult() {
        this.timestamp = LocalDateTime.now();
    }

    public NotificationResult(boolean success, String serviceName) {
        this.success = success;
        this.serviceName = serviceName;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 创建成功结果
     */
    public static NotificationResult success(String serviceName, String message) {
        NotificationResult result = new NotificationResult(true, serviceName);
        result.setMessage(message);
        return result;
    }

    /**
     * 创建成功结果（带响应数据）
     */
    public static NotificationResult success(String serviceName, String message, Object responseData) {
        NotificationResult result = new NotificationResult(true, serviceName);
        result.setMessage(message);
        result.setResponseData(responseData);
        return result;
    }

    /**
     * 创建失败结果
     */
    public static NotificationResult failure(String serviceName, FailureInfo failureInfo) {
        NotificationResult result = new NotificationResult(false, serviceName);
        result.setFailureInfo(failureInfo);
        result.setMessage(failureInfo.getErrorMessage());
        return result;
    }

    /**
     * 创建失败结果（简化版）
     */
    public static NotificationResult failure(String serviceName, String message) {
        NotificationResult result = new NotificationResult(false, serviceName);
        result.setMessage(message);
        return result;
    }

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }

    public void setFailureInfo(FailureInfo failureInfo) {
        this.failureInfo = failureInfo;
    }

    public Object getResponseData() {
        return responseData;
    }

    public void setResponseData(Object responseData) {
        this.responseData = responseData;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "NotificationResult{" +
                "success=" + success +
                ", serviceName='" + serviceName + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}

