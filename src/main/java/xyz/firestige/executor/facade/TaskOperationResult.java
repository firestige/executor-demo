package xyz.firestige.executor.facade;

import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.state.TaskStatus;

/**
 * 任务操作结果
 * 用于暂停、恢复、回滚、重试等操作的返回结果
 */
public class TaskOperationResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 失败信息（失败时）
     */
    private FailureInfo failureInfo;

    /**
     * 消息
     */
    private String message;

    public TaskOperationResult() {
    }

    public TaskOperationResult(boolean success) {
        this.success = success;
    }

    public TaskOperationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    /**
     * 创建成功结果
     */
    public static TaskOperationResult success(String taskId, TaskStatus status, String message) {
        TaskOperationResult result = new TaskOperationResult(true, message);
        result.setTaskId(taskId);
        result.setStatus(status);
        return result;
    }

    /**
     * 创建失败结果
     */
    public static TaskOperationResult failure(String taskId, FailureInfo failureInfo, String message) {
        TaskOperationResult result = new TaskOperationResult(false, message);
        result.setTaskId(taskId);
        result.setFailureInfo(failureInfo);
        return result;
    }

    /**
     * 创建失败结果（简化版）
     */
    public static TaskOperationResult failure(String message) {
        return new TaskOperationResult(false, message);
    }

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }

    public void setFailureInfo(FailureInfo failureInfo) {
        this.failureInfo = failureInfo;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "TaskOperationResult{" +
                "success=" + success +
                ", taskId='" + taskId + '\'' +
                ", status=" + status +
                ", message='" + message + '\'' +
                '}';
    }
}

