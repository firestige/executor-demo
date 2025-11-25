package xyz.firestige.redis.renewal.selector;

import xyz.firestige.redis.renewal.RenewalContext;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

public class FunctionKeySelector implements KeySelector {

    private final Function<RenewalContext, Collection<String>> provider;

    public FunctionKeySelector(Function<RenewalContext, Collection<String>> provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    @Override
    public Collection<String> selectKeys(RenewalContext context) {
        return provider.apply(context);
    }

    @Override
    public String getName() {
        return "FunctionKeySelector";
    }
}

