package xyz.firestige.infrastructure.redis.ack.retry;

import xyz.firestige.infrastructure.redis.ack.api.AckContext;
import xyz.firestige.infrastructure.redis.ack.api.RetryStrategy;

import java.time.Duration;

/**
 * 固定延迟重试策略
 *
 * @author AI
 * @since 1.0
 */
public class FixedDelayRetryStrategy implements RetryStrategy {

    private final int maxAttempts;
    private final Duration delay;

    public FixedDelayRetryStrategy(int maxAttempts, Duration delay) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        if (delay == null || delay.isNegative()) {
            throw new IllegalArgumentException("delay must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.delay = delay;
    }

    @Override
    public Duration nextDelay(int attempt, Throwable lastError, AckContext context) {
        if (attempt >= maxAttempts) {
            return null; // 停止重试
        }
        return delay;
    }
}

