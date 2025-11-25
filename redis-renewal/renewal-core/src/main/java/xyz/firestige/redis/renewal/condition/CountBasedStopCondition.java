package xyz.firestige.redis.renewal.condition;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.StopCondition;

public class CountBasedStopCondition implements StopCondition {
    private final long max;
    public CountBasedStopCondition(long max) { this.max = max; }
    @Override public boolean shouldStop(RenewalContext context) { return context.getRenewalCount() >= max; }
    @Override public String getName() { return "CountBasedStop"; }
}

