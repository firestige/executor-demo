package xyz.firestige.infrastructure.redis.ack.retry;

import xyz.firestige.infrastructure.redis.ack.api.AckContext;
import xyz.firestige.infrastructure.redis.ack.api.RetryStrategy;

import java.time.Duration;

/**
 * 指数退避重试策略
 */
public class ExponentialBackoffRetryStrategy implements RetryStrategy {
    private final int maxAttempts;
    private final Duration initialDelay;
    private final double multiplier;
    private final Duration maxDelay;

    public ExponentialBackoffRetryStrategy(int maxAttempts, Duration initialDelay, double multiplier, Duration maxDelay) {
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts <= 0");
        if (initialDelay == null || initialDelay.isNegative() || initialDelay.isZero()) throw new IllegalArgumentException("invalid initialDelay");
        if (multiplier < 1.0) throw new IllegalArgumentException("multiplier < 1.0");
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.multiplier = multiplier;
        this.maxDelay = maxDelay == null ? Duration.ofSeconds(30) : maxDelay;
    }

    @Override
    public Duration nextDelay(int attempt, Throwable lastError, AckContext context) {
        if (attempt >= maxAttempts) return null;
        double factor = Math.pow(multiplier, attempt - 1);
        long delayMillis = Math.min((long)(initialDelay.toMillis() * factor), maxDelay.toMillis());
        return Duration.ofMillis(delayMillis);
    }
}

