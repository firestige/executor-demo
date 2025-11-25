package xyz.firestige.redis.renewal.strategy.stop;

import xyz.firestige.redis.renewal.RenewalContext;
import xyz.firestige.redis.renewal.selector.KeySelector;

import java.util.Collection;

/**
 * 当选定的键不存在时停止续期的策略实现
 */
public class KeyNotExistsStopStrategy implements StopStrategy {

    private final KeySelector selector;

    public KeyNotExistsStopStrategy(KeySelector selector) {
        this.selector = selector;
    }
    @Override
    public boolean shouldStop(RenewalContext context) {
        Collection<String> keys = selector.selectKeys(context);
        return keys.isEmpty();
    }

    @Override
    public String getName() {
        return "KeyNotExistsStop";
    }
}

