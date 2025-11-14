package xyz.firestige.executor.facade;

import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.validation.ValidationSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务创建结果
 */
public class TaskCreationResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 任务 ID（成功时）
     */
    private String taskId;

    /**
     * 执行单 ID 列表（成功时）
     */
    private List<String> executionUnitIds;

    /**
     * 校验摘要
     */
    private ValidationSummary validationSummary;

    /**
     * 失败信息（失败时）
     */
    private FailureInfo failureInfo;

    /**
     * 消息
     */
    private String message;

    public TaskCreationResult() {
        this.executionUnitIds = new ArrayList<>();
    }

    public TaskCreationResult(boolean success) {
        this.success = success;
        this.executionUnitIds = new ArrayList<>();
    }

    /**
     * 创建成功结果
     */
    public static TaskCreationResult success(String taskId, List<String> executionUnitIds) {
        TaskCreationResult result = new TaskCreationResult(true);
        result.setTaskId(taskId);
        result.setExecutionUnitIds(executionUnitIds);
        result.setMessage("任务创建成功");
        return result;
    }

    /**
     * 创建失败结果
     */
    public static TaskCreationResult failure(FailureInfo failureInfo, String message) {
        TaskCreationResult result = new TaskCreationResult(false);
        result.setFailureInfo(failureInfo);
        result.setMessage(message);
        return result;
    }

    /**
     * 创建校验失败结果
     */
    public static TaskCreationResult validationFailure(ValidationSummary validationSummary) {
        TaskCreationResult result = new TaskCreationResult(false);
        result.setValidationSummary(validationSummary);
        result.setMessage("配置校验失败");
        return result;
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

    public List<String> getExecutionUnitIds() {
        return executionUnitIds;
    }

    public void setExecutionUnitIds(List<String> executionUnitIds) {
        this.executionUnitIds = executionUnitIds;
    }

    public ValidationSummary getValidationSummary() {
        return validationSummary;
    }

    public void setValidationSummary(ValidationSummary validationSummary) {
        this.validationSummary = validationSummary;
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
        return "TaskCreationResult{" +
                "success=" + success +
                ", taskId='" + taskId + '\'' +
                ", executionUnitCount=" + (executionUnitIds != null ? executionUnitIds.size() : 0) +
                ", message='" + message + '\'' +
                '}';
    }
}

