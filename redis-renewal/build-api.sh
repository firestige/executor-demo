#!/bin/bash
# Redis Renewal API 层快速构建脚本

API_DIR="/Users/firestige/Projects/executor-demo/redis-renewal/renewal-api/src/main/java/xyz/firestige/redis/renewal/api"

# 创建 RenewalContext
cat > "$API_DIR/RenewalContext.java" << 'EOF'
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
EOF

# 创建其他接口
cat > "$API_DIR/KeySelector.java" << 'EOF'
package xyz.firestige.redis.renewal.api;

import java.util.List;

@FunctionalInterface
public interface KeySelector {
    List<String> selectKeys(RenewalContext context);
}
EOF

cat > "$API_DIR/KeyGenerator.java" << 'EOF'
package xyz.firestige.redis.renewal.api;

@FunctionalInterface
public interface KeyGenerator {
    String generateKey(RenewalContext context);
}
EOF

cat > "$API_DIR/KeyFilter.java" << 'EOF'
package xyz.firestige.redis.renewal.api;

@FunctionalInterface
public interface KeyFilter {
    boolean shouldRenew(String key, RenewalContext context);
}
EOF

cat > "$API_DIR/FailureHandler.java" << 'EOF'
package xyz.firestige.redis.renewal.api;

@FunctionalInterface
public interface FailureHandler {
    void handleFailure(String key, Throwable error, RenewalContext context);

    static FailureHandler logAndContinue() {
        return (key, error, ctx) ->
            System.err.println("Renewal failed for key: " + key + ", error: " + error.getMessage());
    }
}
EOF

cat > "$API_DIR/LifecycleListener.java" << 'EOF'
package xyz.firestige.redis.renewal.api;

public interface LifecycleListener {
    default void onTaskStarted(String taskId) {}
    default void onTaskPaused(String taskId) {}
    default void onTaskResumed(String taskId) {}
    default void onTaskCompleted(String taskId) {}
    default void onTaskCancelled(String taskId) {}
    default void onRenewalSuccess(String taskId, String key) {}
    default void onRenewalFailure(String taskId, String key, Throwable error) {}
}
EOF

cat > "$API_DIR/RenewalMetricsRecorder.java" << 'EOF'
package xyz.firestige.redis.renewal.api;

import java.time.Duration;

@FunctionalInterface
public interface RenewalMetricsRecorder {
    void recordRenewal(String taskId, String key, boolean success, Duration elapsed);

    static RenewalMetricsRecorder noop() {
        return (taskId, key, success, elapsed) -> {};
    }
}
EOF

cat > "$API_DIR/RedisClientAdapter.java" << 'EOF'
package xyz.firestige.redis.renewal.api;

import java.time.Duration;
import java.util.Set;

public interface RedisClientAdapter {
    boolean expire(String key, Duration ttl);
    boolean exists(String key);
    Duration ttl(String key);
    Set<String> keys(String pattern);
}
EOF

# 创建异常包
mkdir -p "$API_DIR/exception"

cat > "$API_DIR/exception/RenewalException.java" << 'EOF'
package xyz.firestige.redis.renewal.api.exception;

public class RenewalException extends RuntimeException {
    public RenewalException(String message) { super(message); }
    public RenewalException(String message, Throwable cause) { super(message, cause); }
}
EOF

cat > "$API_DIR/exception/RenewalTimeoutException.java" << 'EOF'
package xyz.firestige.redis.renewal.api.exception;

public class RenewalTimeoutException extends RenewalException {
    public RenewalTimeoutException(String message) { super(message); }
}
EOF

cat > "$API_DIR/exception/KeySelectionException.java" << 'EOF'
package xyz.firestige.redis.renewal.api.exception;

public class KeySelectionException extends RenewalException {
    public KeySelectionException(String message, Throwable cause) { super(message, cause); }
}
EOF

# 创建 package-info
cat > "$API_DIR/package-info.java" << 'EOF'
/**
 * Redis Renewal 服务核心 API
 * <p>
 * 提供 Redis Key TTL 自动续期的接口定义。
 * 核心接口：
 * <ul>
 *   <li>{@link xyz.firestige.redis.renewal.api.RenewalService} - 服务入口</li>
 *   <li>{@link xyz.firestige.redis.renewal.api.TtlStrategy} - TTL 策略</li>
 *   <li>{@link xyz.firestige.redis.renewal.api.IntervalStrategy} - 间隔策略</li>
 *   <li>{@link xyz.firestige.redis.renewal.api.StopCondition} - 停止条件</li>
 * </ul>
 */
package xyz.firestige.redis.renewal.api;
EOF

cat > "$API_DIR/exception/package-info.java" << 'EOF'
/**
 * Redis Renewal 异常类型
 */
package xyz.firestige.redis.renewal.api.exception;
EOF

echo "✅ API 层创建完成！"

