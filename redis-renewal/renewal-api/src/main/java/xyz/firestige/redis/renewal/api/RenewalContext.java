package xyz.firestige.redis.renewal.api;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RenewalContext {
    private final String taskId;
    private final String key;
    private int renewalCount = 0;
    private Instant lastRenewalTime;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public RenewalContext(String taskId, String key) {
        this.taskId = taskId;
        this.key = key;
    }

    public String getTaskId() { return taskId; }
    public String getKey() { return key; }
    public int getRenewalCount() { return renewalCount; }
    public void incrementRenewalCount() { renewalCount++; }
    public Instant getLastRenewalTime() { return lastRenewalTime; }
    public void setLastRenewalTime(Instant time) { this.lastRenewalTime = time; }
    public void setAttribute(String key, Object value) { attributes.put(key, value); }
    public Object getAttribute(String key) { return attributes.get(key); }
}
