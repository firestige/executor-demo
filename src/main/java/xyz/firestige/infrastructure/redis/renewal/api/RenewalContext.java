package xyz.firestige.infrastructure.redis.renewal.api;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 续期上下文
 *
 * <p>包含续期执行过程中的状态信息，提供给各个扩展点使用。
 *
 * @author T-018
 * @since 1.0.0
 */
public class RenewalContext {

    private final String taskId;
    private final Instant startTime;
    private final AtomicLong renewalCount;
    private volatile Instant lastRenewalTime;
    private volatile Duration lastCalculatedTtl;
    private final AtomicLong totalSuccessCount;
    private final AtomicLong totalFailureCount;
    private final Map<String, Object> attributes;

    public RenewalContext(String taskId) {
        this.taskId = taskId;
        this.startTime = Instant.now();
        this.renewalCount = new AtomicLong(0);
        this.lastRenewalTime = null;
        this.totalSuccessCount = new AtomicLong(0);
        this.totalFailureCount = new AtomicLong(0);
        this.attributes = new HashMap<>();
    }

    /**
     * 获取任务 ID
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 获取续期次数
     */
    public long getRenewalCount() {
        return renewalCount.get();
    }

    /**
     * 增加续期次数
     */
    public void incrementRenewalCount() {
        renewalCount.incrementAndGet();
    }

    /**
     * 获取任务开始时间
     */
    public Instant getStartTime() {
        return startTime;
    }

    /**
     * 获取上次续期时间
     */
    public Instant getLastRenewalTime() {
        return lastRenewalTime;
    }

    /**
     * 设置上次续期时间
     */
    public void setLastRenewalTime(Instant lastRenewalTime) {
        this.lastRenewalTime = lastRenewalTime;
    }

    /**
     * 获取上次计算的 TTL
     */
    public Duration getLastCalculatedTtl() {
        return lastCalculatedTtl;
    }

    /**
     * 设置上次计算的 TTL
     */
    public void setLastCalculatedTtl(Duration ttl) {
        this.lastCalculatedTtl = ttl;
    }

    /**
     * 获取总成功 Key 数
     */
    public long getTotalSuccessCount() {
        return totalSuccessCount.get();
    }

    /**
     * 增加成功计数
     */
    public void addSuccessCount(long count) {
        totalSuccessCount.addAndGet(count);
    }

    /**
     * 获取总失败 Key 数
     */
    public long getTotalFailureCount() {
        return totalFailureCount.get();
    }

    /**
     * 增加失败计数
     */
    public void addFailureCount(long count) {
        totalFailureCount.addAndGet(count);
    }

    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        long total = totalSuccessCount.get() + totalFailureCount.get();
        return total > 0 ? (double) totalSuccessCount.get() / total * 100 : 0;
    }

    /**
     * 获取运行时长
     */
    public Duration getRunningDuration() {
        return Duration.between(startTime, Instant.now());
    }

    /**
     * 获取自定义属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 设置自定义属性
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 移除自定义属性
     */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }
}

