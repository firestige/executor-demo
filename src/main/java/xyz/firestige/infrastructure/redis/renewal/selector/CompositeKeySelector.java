package xyz.firestige.infrastructure.redis.renewal.selector;

import xyz.firestige.infrastructure.redis.renewal.api.KeySelector;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import java.util.*;

public class CompositeKeySelector implements KeySelector {
    private final List<KeySelector> delegates;
    public CompositeKeySelector(List<KeySelector> delegates) { this.delegates = List.copyOf(delegates); }
    public CompositeKeySelector(KeySelector... selectors){ this(Arrays.asList(selectors)); }
    @Override public Collection<String> selectKeys(RenewalContext context) {
        Set<String> merged = new LinkedHashSet<>();
        for (KeySelector k : delegates) merged.addAll(k.selectKeys(context));
        return merged;
    }
    @Override public String getName() { return "CompositeKeySelector"; }
}

