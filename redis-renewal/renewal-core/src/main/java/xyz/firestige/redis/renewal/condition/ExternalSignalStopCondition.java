package xyz.firestige.redis.renewal.condition;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.StopCondition;

import java.util.Objects;
import java.util.function.Supplier;

public class ExternalSignalStopCondition implements StopCondition {
    private final Supplier<Boolean> signalSupplier;
    public ExternalSignalStopCondition(Supplier<Boolean> signalSupplier) { this.signalSupplier = Objects.requireNonNull(signalSupplier); }
    @Override public boolean shouldStop(RenewalContext context) { return Boolean.TRUE.equals(signalSupplier.get()); }
    @Override public String getName() { return "ExternalSignalStop"; }
}

