package xyz.firestige.redis.renewal;

import java.time.Instant;

/**
 * 续期结果
 *
 * <p>记录单次续期操作的结果。
 *
 * @author T-018
 * @since 1.0.0
 */
public class RenewalResult {

    private final String taskId;
    private final long successCount;
    private final long failureCount;
    private final long durationMillis;
    private final Instant timestamp;
    private final boolean success;
    private final Throwable error;

    private RenewalResult(String taskId, long successCount, long failureCount,
                         long durationMillis, boolean success, Throwable error) {
        this.taskId = taskId;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.durationMillis = durationMillis;
        this.timestamp = Instant.now();
        this.success = success;
        this.error = error;
    }

    /**
     * 创建成功结果
     */
    public static RenewalResult success(String taskId, long successCount,
                                       long failureCount, long durationMillis) {
        return new RenewalResult(taskId, successCount, failureCount,
                               durationMillis, true, null);
    }

    /**
     * 创建失败结果
     */
    public static RenewalResult failure(String taskId, Throwable error) {
        return new RenewalResult(taskId, 0, 0, 0, false, error);
    }

    // Getters

    public String getTaskId() {
        return taskId;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        return String.format("RenewalResult{taskId='%s', success=%d, failure=%d, duration=%dms}",
                           taskId, successCount, failureCount, durationMillis);
    }
}

