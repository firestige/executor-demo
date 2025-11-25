package xyz.firestige.redis.renewal.strategy.interval;

import xyz.firestige.redis.renewal.RenewalContext;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机抖动间隔策略
 */
public class RandomizedIntervalStrategy implements IntervalStrategy {

    private final Duration base;

    private final Duration jitter;

    public RandomizedIntervalStrategy(Duration base, Duration jitter) {
        this.base = base;
        this.jitter = jitter;
    }

    @Override
    public Duration nextInterval(RenewalContext context) {
        long j = ThreadLocalRandom.current().nextLong(-jitter.toMillis(), jitter.toMillis());
        long ms = Math.max(10, base.toMillis() + j);
        return Duration.ofMillis(ms);
    }

    @Override
    public String getName() {
        return "RandomizedInterval";
    }
}

