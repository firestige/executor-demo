package xyz.firestige.executor.domain.plan;

/**
 * Plan 聚合的状态
 */
public enum PlanStatus {
    CREATED,
    VALIDATING,
    READY,
    RUNNING,
    PARTIAL_FAILED,
    COMPLETED,
    ROLLING_BACK,
    ROLLED_BACK,
    FAILED,
    CANCELLED
}

