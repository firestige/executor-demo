package xyz.firestige.redis.renewal.strategy.interval;

import xyz.firestige.redis.renewal.RenewalContext;

import java.time.Duration;
import java.util.Objects;

/**
 * 固定间隔策略
 */
public class FixedIntervalStrategy implements IntervalStrategy {

    private final Duration interval;

    public FixedIntervalStrategy(Duration interval) {
        this.interval = Objects.requireNonNull(interval);
    }

    @Override
    public Duration nextInterval(RenewalContext context) {
        return interval;
    }

    @Override
    public String getName() {
        return "FixedInterval";
    }
}

