package xyz.firestige.deploy.application.plan;

import xyz.firestige.deploy.domain.plan.PlanInfo;
import xyz.firestige.deploy.domain.shared.validation.ValidationSummary;
import xyz.firestige.deploy.domain.shared.vo.PlanId;

/**
 * Plan 创建上下文（RF-10 重构）
 *
 * 职责：封装 Plan 创建的结果，包括验证结果和 Plan 信息
 *
 * @since DDD 重构 Phase 18 - RF-10
 */
public class PlanCreationContext {

    private final boolean success;
    private final PlanInfo planInfo;
    private final ValidationSummary validationSummary;

    private PlanCreationContext(boolean success, PlanInfo planInfo, ValidationSummary validationSummary) {
        this.success = success;
        this.planInfo = planInfo;
        this.validationSummary = validationSummary;
    }

    /**
     * 创建成功的上下文
     *
     * @param planInfo Plan 信息
     * @return PlanCreationContext
     */
    public static PlanCreationContext success(PlanInfo planInfo) {
        return new PlanCreationContext(true, planInfo, null);
    }

    /**
     * 创建验证失败的上下文
     *
     * @param validationSummary 验证摘要
     * @return PlanCreationContext
     */
    public static PlanCreationContext validationFailure(ValidationSummary validationSummary) {
        return new PlanCreationContext(false, null, validationSummary);
    }

    public boolean isSuccess() {
        return success;
    }

    public PlanInfo getPlanInfo() {
        return planInfo;
    }

    /**
     * 获取 Plan ID（RF-12 新增）
     *
     * @return Plan ID，如果创建失败则返回 null
     */
    public PlanId getPlanId() {
        return planInfo != null ? planInfo.getPlanId() : null;
    }

    public ValidationSummary getValidationSummary() {
        return validationSummary;
    }

    public boolean hasValidationErrors() {
        return validationSummary != null && validationSummary.hasErrors();
    }
}

