package xyz.firestige.infrastructure.redis.renewal.interval;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalIntervalStrategy;

import java.time.Duration;

public class AdaptiveIntervalStrategy implements RenewalIntervalStrategy {
    private final double ratio;
    public AdaptiveIntervalStrategy(double ratio) { this.ratio = ratio; }
    @Override public Duration calculateInterval(RenewalContext context) {
        // 简化：使用固定基准 10s * ratio；后续可在上下文记录上次 TTL
        long baseMs = 10_000L;
        return Duration.ofMillis((long)(baseMs * ratio));
    }
    @Override public String getName() { return "AdaptiveInterval"; }
}

