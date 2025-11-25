package xyz.firestige.redis.renewal.strategy.stop;

import xyz.firestige.redis.renewal.RenewalContext;

/**
 * 永不停止策略
 */
public class NeverStopStrategy implements StopStrategy {

    @Override
    public boolean shouldStop(RenewalContext context) {
        return false;
    }

    @Override
    public String getName() {
        return "NeverStop";
    }
}

