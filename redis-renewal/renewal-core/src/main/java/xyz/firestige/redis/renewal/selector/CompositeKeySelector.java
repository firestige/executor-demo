package xyz.firestige.redis.renewal.selector;

import xyz.firestige.redis.renewal.RenewalContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 组合键选择器
 */
public class CompositeKeySelector implements KeySelector {

    private final List<KeySelector> delegates;

    public CompositeKeySelector(List<KeySelector> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    public CompositeKeySelector(KeySelector... selectors){
        this(Arrays.asList(selectors));
    }
    @Override
    public Collection<String> selectKeys(RenewalContext context) {
        Set<String> merged = new LinkedHashSet<>();
        for (KeySelector k : delegates) {
            merged.addAll(k.selectKeys(context));
        }
        return merged;
    }

    @Override
    public String getName() { return "CompositeKeySelector"; }
}

