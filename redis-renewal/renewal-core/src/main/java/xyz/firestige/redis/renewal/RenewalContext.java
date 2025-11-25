package xyz.firestige.redis.renewal;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RenewalContext {
    private final String taskId;
    private final String key;
    private int renewalCount = 0;
    private long successCount = 0;
    private long failureCount = 0;
    private Instant lastRenewalTime;
    private Duration lastTtl;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * 构造任务级上下文（用于多 key 任务）
     */
    public RenewalContext(String taskId) {
        this.taskId = taskId;
        this.key = null;
    }

    /**
     * 构造单 key 上下文
     */
    public RenewalContext(String taskId, String key) {
        this.taskId = taskId;
        this.key = key;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getKey() {
        return key;
    }
    public int getRenewalCount() {
        return renewalCount;
    }

    public void incrementRenewalCount() {
        renewalCount++;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public void addSuccessCount(long count) {
        this.successCount += count;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public void addFailureCount(long count) {
        this.failureCount += count;
    }

    public Instant getLastRenewalTime() {
        return lastRenewalTime;
    }

    public void setLastTtl(Duration lastTtl) {
        this.lastTtl = lastTtl;
    }

    public Duration getLastTtl() {
        return lastTtl;
    }

    public void setLastRenewalTime(Instant time) {
        this.lastRenewalTime = time;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }
}
