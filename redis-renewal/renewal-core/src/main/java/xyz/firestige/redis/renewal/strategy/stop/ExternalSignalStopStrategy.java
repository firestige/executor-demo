package xyz.firestige.redis.renewal.strategy.stop;

import xyz.firestige.redis.renewal.RenewalContext;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 基于外部信号的停止策略实现
 * <p>
 * 通过一个布尔值函数接口决定是否停止续期
 */
public class ExternalSignalStopStrategy implements StopStrategy {

    private final Supplier<Boolean> signalSupplier;

    public ExternalSignalStopStrategy(Supplier<Boolean> signalSupplier) {
        this.signalSupplier = Objects.requireNonNull(signalSupplier);
    }

    @Override
    public boolean shouldStop(RenewalContext context) {
        return Boolean.TRUE.equals(signalSupplier.get());
    }

    @Override
    public String getName() {
        return "ExternalSignalStop";
    }
}

