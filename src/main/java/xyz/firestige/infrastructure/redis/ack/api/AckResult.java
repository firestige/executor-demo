package xyz.firestige.infrastructure.redis.ack.api;

import java.time.Duration;
import java.time.Instant;

/**
 * ACK 执行结果
 *
 * @author AI
 * @since 1.0
 */
public class AckResult {

    private final boolean success;
    private final String expectedFootprint;
    private final String actualFootprint;
    private final int attempts;
    private final Duration elapsed;
    private final String reason;
    private final Throwable error;

    private AckResult(boolean success, String expectedFootprint, String actualFootprint,
                     int attempts, Duration elapsed, String reason, Throwable error) {
        this.success = success;
        this.expectedFootprint = expectedFootprint;
        this.actualFootprint = actualFootprint;
        this.attempts = attempts;
        this.elapsed = elapsed;
        this.reason = reason;
        this.error = error;
    }

    // Getters

    public boolean isSuccess() {
        return success;
    }

    public boolean isTimeout() {
        return !success && "TIMEOUT".equals(reason);
    }

    public boolean isFootprintMismatch() {
        return !success && "MISMATCH".equals(reason);
    }

    public boolean isError() {
        return !success && error != null;
    }

    public String getExpectedFootprint() {
        return expectedFootprint;
    }

    public String getActualFootprint() {
        return actualFootprint;
    }

    public int getAttempts() {
        return attempts;
    }

    public Duration getElapsed() {
        return elapsed;
    }

    public String getReason() {
        return reason;
    }

    public Throwable getError() {
        return error;
    }

    // Factory methods

    public static AckResult success(String expectedFootprint, String actualFootprint,
                                   int attempts, Duration elapsed) {
        return new AckResult(true, expectedFootprint, actualFootprint, attempts, elapsed, null, null);
    }

    public static AckResult mismatch(String expectedFootprint, String actualFootprint,
                                    int attempts, Duration elapsed) {
        return new AckResult(false, expectedFootprint, actualFootprint, attempts, elapsed, "MISMATCH", null);
    }

    public static AckResult timeout(String expectedFootprint, int attempts, Duration elapsed) {
        return new AckResult(false, expectedFootprint, null, attempts, elapsed, "TIMEOUT", null);
    }

    public static AckResult error(String expectedFootprint, int attempts, Duration elapsed, Throwable error) {
        return new AckResult(false, expectedFootprint, null, attempts, elapsed, "ERROR", error);
    }

    @Override
    public String toString() {
        return "AckResult{" +
                "success=" + success +
                ", expectedFootprint='" + expectedFootprint + '\'' +
                ", actualFootprint='" + actualFootprint + '\'' +
                ", attempts=" + attempts +
                ", elapsed=" + elapsed +
                ", reason='" + reason + '\'' +
                '}';
    }
}

