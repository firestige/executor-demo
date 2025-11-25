package xyz.firestige.redis.renewal.strategy;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalStrategy;

import java.time.Duration;
import java.util.Objects;

public class FixedTtlStrategy implements RenewalStrategy {
    private final Duration ttl;
    public FixedTtlStrategy(Duration ttl) { this.ttl = Objects.requireNonNull(ttl); }
    @Override public Duration calculateTtl(RenewalContext context) { return ttl; }
    @Override public boolean shouldContinue(RenewalContext context) { return true; }
    @Override public String getName() { return "FixedTtl"; }
}

