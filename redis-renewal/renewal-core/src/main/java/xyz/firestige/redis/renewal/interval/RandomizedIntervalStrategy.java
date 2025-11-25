package xyz.firestige.infrastructure.redis.renewal.interval;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalIntervalStrategy;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public class RandomizedIntervalStrategy implements RenewalIntervalStrategy {
    private final Duration base;
    private final Duration jitter;
    public RandomizedIntervalStrategy(Duration base, Duration jitter) {
        this.base = base; this.jitter = jitter; }
    @Override public Duration calculateInterval(RenewalContext context) {
        long j = ThreadLocalRandom.current().nextLong(-jitter.toMillis(), jitter.toMillis());
        long ms = Math.max(10, base.toMillis() + j);
        return Duration.ofMillis(ms);
    }
    @Override public String getName() { return "RandomizedInterval"; }
}

