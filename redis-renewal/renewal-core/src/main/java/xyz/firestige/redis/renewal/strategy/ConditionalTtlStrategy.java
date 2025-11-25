package xyz.firestige.redis.renewal.strategy;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalStrategy;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class ConditionalTtlStrategy implements RenewalStrategy {
    private final Function<RenewalContext, Duration> ttlFunc;
    private final Predicate<RenewalContext> continuePredicate;
    public ConditionalTtlStrategy(Function<RenewalContext, Duration> ttlFunc, Predicate<RenewalContext> continuePredicate) {
        this.ttlFunc = Objects.requireNonNull(ttlFunc);
        this.continuePredicate = Objects.requireNonNull(continuePredicate);
    }
    @Override public Duration calculateTtl(RenewalContext context) { return Objects.requireNonNull(ttlFunc.apply(context)); }
    @Override public boolean shouldContinue(RenewalContext context) { return continuePredicate.test(context); }
    @Override public String getName() { return "ConditionalTtl"; }
}

