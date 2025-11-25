package xyz.firestige.redis.renewal.api;

import java.time.Duration;

@FunctionalInterface
public interface RenewalMetricsRecorder {
    void recordRenewal(String taskId, String key, boolean success, Duration elapsed);

    static RenewalMetricsRecorder noop() {
        return (taskId, key, success, elapsed) -> {};
    }
}
