package xyz.firestige.redis.renewal.strategy.ttl;

import xyz.firestige.redis.renewal.RenewalContext;

import java.time.Duration;
import java.util.Objects;

/**
 * 固定 TTL 策略
 */
public class FixedTtlStrategy implements TtlStrategy {

    private final Duration ttl;

    public FixedTtlStrategy(Duration ttl) {
        this.ttl = Objects.requireNonNull(ttl);
    }

    @Override
    public Duration nextTtl(RenewalContext context) {
        return ttl;
    }

    @Override
    public String getName() {
        return "FixedTtl";
    }
}

