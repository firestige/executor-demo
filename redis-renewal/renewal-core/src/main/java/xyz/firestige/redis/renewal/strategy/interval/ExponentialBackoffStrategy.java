package xyz.firestige.redis.renewal.strategy.interval;

import xyz.firestige.redis.renewal.RenewalContext;

import java.time.Duration;
import java.util.Objects;

/**
 * 指数退避间隔策略
 */
public class ExponentialBackoffStrategy implements IntervalStrategy {

    private final Duration initial;

    private final Duration max;

    private final double factor;

    public ExponentialBackoffStrategy(Duration initial, Duration max, double factor) {
        this.initial = Objects.requireNonNull(initial);
        this.max = Objects.requireNonNull(max);
        this.factor = factor;
    }

    @Override
    public Duration nextInterval(RenewalContext context) {
        long n = context.getRenewalCount();
        double pow = Math.pow(factor, n);
        long candidate = (long)(initial.toMillis() * pow);
        long capped = Math.min(candidate, max.toMillis());
        return Duration.ofMillis(capped);
    }

    @Override
    public String getName() {
        return "ExponentialBackoff";
    }
}

