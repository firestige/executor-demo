package xyz.firestige.deploy.application.plan;

/**
 * Plan 创建异常（RF-10 重构）
 *
 * 职责：表示 Plan 创建过程中的异常
 *
 * @since DDD 重构 Phase 18 - RF-10
 */
public class PlanCreationException extends RuntimeException {

    public PlanCreationException(String message) {
        super(message);
    }

    public PlanCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}

