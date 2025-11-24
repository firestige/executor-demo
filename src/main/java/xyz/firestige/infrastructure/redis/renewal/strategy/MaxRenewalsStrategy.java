package xyz.firestige.infrastructure.redis.renewal.strategy;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalStrategy;

import java.time.Duration;
import java.util.Objects;

public class MaxRenewalsStrategy implements RenewalStrategy {
    private final Duration ttl;
    private final long maxTimes;
    public MaxRenewalsStrategy(Duration ttl, long maxTimes) {
        this.ttl = Objects.requireNonNull(ttl);
        this.maxTimes = maxTimes;
    }
    @Override public Duration calculateTtl(RenewalContext context) { return ttl; }
    @Override public boolean shouldContinue(RenewalContext context) { return context.getRenewalCount() < maxTimes; }
    @Override public String getName() { return "MaxRenewals"; }
}

