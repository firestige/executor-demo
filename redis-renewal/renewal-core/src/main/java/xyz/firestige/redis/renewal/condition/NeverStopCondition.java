package xyz.firestige.infrastructure.redis.renewal.condition;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.StopCondition;

public class NeverStopCondition implements StopCondition {
    @Override public boolean shouldStop(RenewalContext context) { return false; }
    @Override public String getName() { return "NeverStop"; }
}

