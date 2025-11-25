package xyz.firestige.redis.renewal.interval;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalIntervalStrategy;

import java.time.Duration;

/**
 * 自适应间隔策略
 * <p>根据上次计算的 TTL 动态调整续期间隔
 */
public class AdaptiveIntervalStrategy implements RenewalIntervalStrategy {
    private final double ratio;
    private final Duration fallback;

    public AdaptiveIntervalStrategy(double ratio) {
        this(ratio, Duration.ofSeconds(10));
    }

    public AdaptiveIntervalStrategy(double ratio, Duration fallback) {
        this.ratio = ratio;
        this.fallback = fallback;
    }

    @Override
    public Duration calculateInterval(RenewalContext context) {
        Duration lastTtl = context.getLastCalculatedTtl();
        if (lastTtl == null) {
            return fallback;
        }
        long intervalMs = (long)(lastTtl.toMillis() * ratio);
        return Duration.ofMillis(Math.max(intervalMs, 10));
    }

    @Override
    public String getName() {
        return "AdaptiveInterval";
    }
}

