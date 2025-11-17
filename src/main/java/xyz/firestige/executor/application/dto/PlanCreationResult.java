package xyz.firestige.executor.application.dto;

import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.validation.ValidationSummary;

/**
 * Plan 创建结果
 * 表达 Plan 聚合的创建结果，包含 Plan 和其包含的 Task 信息
 */
public class PlanCreationResult {

    private boolean success;
    private PlanInfo planInfo;              // Plan 聚合信息（值对象）
    private ValidationSummary validationSummary;
    private FailureInfo failureInfo;
    private String message;

    public PlanCreationResult() {
    }

    // 静态工厂方法

    /**
     * 创建成功结果
     */
    public static PlanCreationResult success(PlanInfo planInfo) {
        PlanCreationResult result = new PlanCreationResult();
        result.success = true;
        result.planInfo = planInfo;
        result.message = "Plan 创建成功";
        return result;
    }

    /**
     * 创建校验失败结果
     */
    public static PlanCreationResult validationFailure(ValidationSummary summary) {
        PlanCreationResult result = new PlanCreationResult();
        result.success = false;
        result.validationSummary = summary;
        result.message = "配置校验失败";
        return result;
    }

    /**
     * 创建失败结果
     */
    public static PlanCreationResult failure(FailureInfo failureInfo, String message) {
        PlanCreationResult result = new PlanCreationResult();
        result.success = false;
        result.failureInfo = failureInfo;
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

    public PlanInfo getPlanInfo() {
        return planInfo;
    }

    public void setPlanInfo(PlanInfo planInfo) {
        this.planInfo = planInfo;
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
        return "PlanCreationResult{" +
                "success=" + success +
                ", planInfo=" + planInfo +
                ", message='" + message + '\'' +
                '}';
    }
}

