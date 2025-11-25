package xyz.firestige.redis.renewal.strategy;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalStrategy;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

public class DynamicTtlStrategy implements RenewalStrategy {
    private final Function<RenewalContext, Duration> calculator;
    public DynamicTtlStrategy(Function<RenewalContext, Duration> calculator) { this.calculator = Objects.requireNonNull(calculator); }
    @Override public Duration calculateTtl(RenewalContext context) { return Objects.requireNonNull(calculator.apply(context)); }
    @Override public boolean shouldContinue(RenewalContext context) { return true; }
    @Override public String getName() { return "DynamicTtl"; }
}

