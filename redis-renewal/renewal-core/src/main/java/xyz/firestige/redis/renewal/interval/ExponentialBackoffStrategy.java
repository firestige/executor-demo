package xyz.firestige.redis.renewal.interval;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalIntervalStrategy;

import java.time.Duration;
import java.util.Objects;

public class ExponentialBackoffStrategy implements RenewalIntervalStrategy {
    private final Duration initial;
    private final Duration max;
    private final double factor;
    public ExponentialBackoffStrategy(Duration initial, Duration max, double factor) {
        this.initial = Objects.requireNonNull(initial);
        this.max = Objects.requireNonNull(max);
        this.factor = factor;
    }
    @Override public Duration calculateInterval(RenewalContext context) {
        long n = context.getRenewalCount();
        double pow = Math.pow(factor, n);
        long candidate = (long)(initial.toMillis() * pow);
        long capped = Math.min(candidate, max.toMillis());
        return Duration.ofMillis(capped);
    }
    @Override public String getName() { return "ExponentialBackoff"; }
}

