package xyz.firestige.redis.renewal.metrics;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 续期指标收集器
 * <p>线程安全的指标收集，用于监控和可观测性
 */
public class RenewalMetricsCollector {

    private final AtomicLong totalRenewals = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong taskFailures = new AtomicLong(0);
    private volatile Instant lastRenewalTime;
    private volatile int activeTaskCount = 0;

    /**
     * 记录续期执行
     */
    public void recordRenewal(long successKeys, long failureKeys) {
        totalRenewals.incrementAndGet();
        successCount.addAndGet(successKeys);
        failureCount.addAndGet(failureKeys);
        lastRenewalTime = Instant.now();
    }

    /**
     * 记录任务失败
     */
    public void recordTaskFailure() {
        taskFailures.incrementAndGet();
    }

    /**
     * 更新活跃任务数
     */
    public void updateActiveTaskCount(int count) {
        this.activeTaskCount = count;
    }

    /**
     * 获取总续期次数
     */
    public long getTotalRenewals() {
        return totalRenewals.get();
    }

    /**
     * 获取成功 Key 总数
     */
    public long getSuccessCount() {
        return successCount.get();
    }

    /**
     * 获取失败 Key 总数
     */
    public long getFailureCount() {
        return failureCount.get();
    }

    /**
     * 获取任务失败总数
     */
    public long getTaskFailures() {
        return taskFailures.get();
    }

    /**
     * 获取成功率（百分比）
     */
    public double getSuccessRate() {
        long total = successCount.get() + failureCount.get();
        return total > 0 ? (double) successCount.get() / total * 100 : 100.0;
    }

    /**
     * 获取最后续期时间
     */
    public Instant getLastRenewalTime() {
        return lastRenewalTime;
    }

    /**
     * 获取活跃任务数
     */
    public int getActiveTaskCount() {
        return activeTaskCount;
    }

    /**
     * 重置指标
     */
    public void reset() {
        totalRenewals.set(0);
        successCount.set(0);
        failureCount.set(0);
        taskFailures.set(0);
        lastRenewalTime = null;
    }

    /**
     * 获取指标快照
     */
    public MetricsSnapshot snapshot() {
        return new MetricsSnapshot(
            totalRenewals.get(),
            successCount.get(),
            failureCount.get(),
            taskFailures.get(),
            getSuccessRate(),
            lastRenewalTime,
            activeTaskCount
        );
    }

    /**
     * 指标快照（不可变）
     */
    public static class MetricsSnapshot {
        private final long totalRenewals;
        private final long successCount;
        private final long failureCount;
        private final long taskFailures;
        private final double successRate;
        private final Instant lastRenewalTime;
        private final int activeTaskCount;

        public MetricsSnapshot(long totalRenewals, long successCount, long failureCount,
                             long taskFailures, double successRate, Instant lastRenewalTime,
                             int activeTaskCount) {
            this.totalRenewals = totalRenewals;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.taskFailures = taskFailures;
            this.successRate = successRate;
            this.lastRenewalTime = lastRenewalTime;
            this.activeTaskCount = activeTaskCount;
        }

        public long getTotalRenewals() { return totalRenewals; }
        public long getSuccessCount() { return successCount; }
        public long getFailureCount() { return failureCount; }
        public long getTaskFailures() { return taskFailures; }
        public double getSuccessRate() { return successRate; }
        public Instant getLastRenewalTime() { return lastRenewalTime; }
        public int getActiveTaskCount() { return activeTaskCount; }
    }
}

