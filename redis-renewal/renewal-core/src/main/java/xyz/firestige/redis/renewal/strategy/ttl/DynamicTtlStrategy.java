package xyz.firestige.redis.renewal.strategy.ttl;

import xyz.firestige.redis.renewal.RenewalContext;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

/**
 * 动态 TTL 策略
 */
public class DynamicTtlStrategy implements TtlStrategy {

    private final Function<RenewalContext, Duration> calculator;

    public DynamicTtlStrategy(Function<RenewalContext, Duration> calculator) {
        this.calculator = Objects.requireNonNull(calculator);
    }

    @Override
    public Duration nextTtl(RenewalContext context) {
        return Objects.requireNonNull(calculator.apply(context));
    }

    @Override
    public String getName() {
        return "DynamicTtl";
    }
}

