package xyz.firestige.infrastructure.redis.renewal.interval;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalIntervalStrategy;

import java.time.Duration;
import java.util.Objects;

public class FixedIntervalStrategy implements RenewalIntervalStrategy {
    private final Duration interval;
    public FixedIntervalStrategy(Duration interval) { this.interval = Objects.requireNonNull(interval); }
    @Override public Duration calculateInterval(RenewalContext context) { return interval; }
    @Override public String getName() { return "FixedInterval"; }
}

