package xyz.firestige.redis.renewal.strategy.stop;

import xyz.firestige.redis.renewal.RenewalContext;

/**
 * 基于次数的停止策略实现
 */
public class CountBasedStopStrategy implements StopStrategy {

    private final long max;

    public CountBasedStopStrategy(long max) {
        this.max = max;
    }

    @Override
    public boolean shouldStop(RenewalContext context) {
        return context.getRenewalCount() >= max;
    }

    @Override
    public String getName() {
        return "CountBasedStop";
    }
}

