package xyz.firestige.infrastructure.redis.renewal.strategy;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalStrategy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class UntilTimeStrategy implements RenewalStrategy {
    private final Instant endTime;
    private final Duration baseTtl;
    public UntilTimeStrategy(Instant endTime, Duration baseTtl) {
        this.endTime = Objects.requireNonNull(endTime);
        this.baseTtl = Objects.requireNonNull(baseTtl);
    }
    @Override public Duration calculateTtl(RenewalContext context) {
        Duration remaining = Duration.between(Instant.now(), endTime);
        if (remaining.isNegative()) return Duration.ofSeconds(1);
        return remaining.compareTo(baseTtl) < 0 ? remaining : baseTtl;
    }
    @Override public boolean shouldContinue(RenewalContext context) { return Instant.now().isBefore(endTime); }
    @Override public String getName() { return "UntilTime"; }
}

