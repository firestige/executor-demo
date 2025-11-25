package xyz.firestige.redis.renewal.condition;

import xyz.firestige.infrastructure.redis.renewal.api.KeySelector;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.StopCondition;

import java.util.Collection;

public class KeyNotExistsStopCondition implements StopCondition {
    private final KeySelector selector;
    public KeyNotExistsStopCondition(KeySelector selector) { this.selector = selector; }
    @Override public boolean shouldStop(RenewalContext context) {
        Collection<String> keys = selector.selectKeys(context);
        return keys.isEmpty();
    }
    @Override public String getName() { return "KeyNotExistsStop"; }
}

