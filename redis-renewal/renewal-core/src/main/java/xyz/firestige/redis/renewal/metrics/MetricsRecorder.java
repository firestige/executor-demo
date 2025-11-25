package xyz.firestige.redis.renewal.metrics;

import xyz.firestige.redis.renewal.Named;

import java.time.Duration;

/**
 * 指标记录器接口
 */
@FunctionalInterface
public interface MetricsRecorder extends Named {

    void recordRenewal(String taskId, String key, boolean success, Duration elapsed);

    static MetricsRecorder noop() {
        return (taskId, key, success, elapsed) -> {};
    }
}
