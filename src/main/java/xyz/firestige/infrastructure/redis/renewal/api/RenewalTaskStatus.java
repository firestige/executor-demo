package xyz.firestige.infrastructure.redis.renewal.api;

import java.time.Instant;

/**
 * 续期任务状态
 *
 * <p>包含任务的运行状态和统计信息。
 *
 * @author T-018
 * @since 1.0.0
 */
public class RenewalTaskStatus {

    private final String taskId;
    private final State state;
    private final long renewalCount;
    private final Instant startTime;
    private final Instant lastRenewalTime;
    private final long totalSuccessCount;
    private final long totalFailureCount;
    private final double successRate;

    public RenewalTaskStatus(String taskId, State state, RenewalContext context) {
        this.taskId = taskId;
        this.state = state;
        this.renewalCount = context.getRenewalCount();
        this.startTime = context.getStartTime();
        this.lastRenewalTime = context.getLastRenewalTime();
        this.totalSuccessCount = context.getTotalSuccessCount();
        this.totalFailureCount = context.getTotalFailureCount();
        this.successRate = context.getSuccessRate();
    }

    // Getters
    public String getTaskId() { return taskId; }
    public State getState() { return state; }
    public long getRenewalCount() { return renewalCount; }
    public Instant getStartTime() { return startTime; }
    public Instant getLastRenewalTime() { return lastRenewalTime; }
    public long getTotalSuccessCount() { return totalSuccessCount; }
    public long getTotalFailureCount() { return totalFailureCount; }
    public double getSuccessRate() { return successRate; }

    /**
     * 任务状态枚举
     */
    public enum State {
        RUNNING,    // 运行中
        PAUSED,     // 已暂停
        COMPLETED,  // 已完成
        FAILED      // 失败
    }

    @Override
    public String toString() {
        return String.format("RenewalTaskStatus{taskId='%s', state=%s, renewals=%d, successRate=%.2f%%}",
                           taskId, state, renewalCount, successRate);
    }
}

