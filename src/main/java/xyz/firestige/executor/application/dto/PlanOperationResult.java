package xyz.firestige.executor.application.dto;

import xyz.firestige.executor.domain.plan.PlanStatus;
import xyz.firestige.executor.exception.FailureInfo;

/**
 * Plan 操作结果
 * 用于 Plan 级别的操作（暂停、恢复、回滚、重试）
 * 明确区分 Plan 聚合的操作结果
 */
public class PlanOperationResult {

    private boolean success;
    private String planId;
    private PlanStatus status;
    private FailureInfo failureInfo;
    private String message;

    public PlanOperationResult() {
    }

    // 静态工厂方法

    /**
     * 创建成功结果
     */
    public static PlanOperationResult success(String planId, PlanStatus status, String message) {
        PlanOperationResult result = new PlanOperationResult();
        result.success = true;
        result.planId = planId;
        result.status = status;
        result.message = message;
        return result;
    }

    /**
     * 创建失败结果
     */
    public static PlanOperationResult failure(String planId, FailureInfo failureInfo, String message) {
        PlanOperationResult result = new PlanOperationResult();
        result.success = false;
        result.planId = planId;
        result.failureInfo = failureInfo;
        result.message = message;
        return result;
    }

    /**
     * 创建失败结果（简化版）
     */
    public static PlanOperationResult failure(String message) {
        PlanOperationResult result = new PlanOperationResult();
        result.success = false;
        result.message = message;
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

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
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
        return "PlanOperationResult{" +
                "success=" + success +
                ", planId='" + planId + '\'' +
                ", status=" + status +
                ", message='" + message + '\'' +
                '}';
    }
}

