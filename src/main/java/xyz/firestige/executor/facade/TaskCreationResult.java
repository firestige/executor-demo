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
    private String planId;

    /**
     * 执行单 ID 列表（成功时）
     */
    private List<String> taskIds;

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
        this.taskIds = new ArrayList<>();
    }

    public TaskCreationResult(boolean success) {
        this.success = success;
        this.taskIds = new ArrayList<>();
    }

    /**
     * 创建成功结果
     * 
     * @param planId 任务 ID
     * @param taskIds 执行任务 ID 列表
     * @return 成功结果
     */
    public static TaskCreationResult success(String planId, List<String> taskIds) {
        TaskCreationResult result = new TaskCreationResult(true);
        result.setPlanId(planId);
        result.setTaskIds(taskIds);
        result.setMessage("任务创建成功");
        return result;
    }

    /**
     * 创建失败结果
     * 
     * @param failureInfo 失败信息
     * @param message 失败消息
     * @return 失败结果
     */
    public static TaskCreationResult failure(FailureInfo failureInfo, String message) {
        TaskCreationResult result = new TaskCreationResult(false);
        result.setFailureInfo(failureInfo);
        result.setMessage(message);
        return result;
    }

    /**
     * 创建校验失败结果
     * 
     * @param validationSummary 校验摘要
     * @return 校验失败结果
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

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public List<String> getTaskIds() {
        return taskIds;
    }

    public void setTaskIds(List<String> taskIds) {
        this.taskIds = taskIds;
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
                ", taskId='" + planId + '\'' +
                ", executionUnitCount=" + (taskIds != null ? taskIds.size() : 0) +
                ", message='" + message + '\'' +
                '}';
    }
}

