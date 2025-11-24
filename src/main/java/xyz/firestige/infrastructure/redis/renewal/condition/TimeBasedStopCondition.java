package xyz.firestige.infrastructure.redis.renewal.condition;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.StopCondition;

import java.time.Instant;
import java.util.Objects;

public class TimeBasedStopCondition implements StopCondition {
    private final Instant endTime;
    public TimeBasedStopCondition(Instant endTime) { this.endTime = Objects.requireNonNull(endTime); }
    @Override public boolean shouldStop(RenewalContext context) { return Instant.now().isAfter(endTime); }
    @Override public String getName() { return "TimeBasedStop"; }
}

