package xyz.firestige.redis.renewal.selector;

import xyz.firestige.redis.renewal.RedisClient;
import xyz.firestige.redis.renewal.RenewalContext;

import java.util.Collection;
import java.util.Objects;

public class PatternKeySelector implements KeySelector {

    private final String pattern;

    private final RedisClient client;

    private final int scanCount;

    public PatternKeySelector(String pattern, RedisClient client, int scanCount) {
        this.pattern = Objects.requireNonNull(pattern);
        this.client = Objects.requireNonNull(client);
        this.scanCount = scanCount;
    }


    @Override
    public Collection<String> selectKeys(RenewalContext context) {
        return client.scan(pattern, scanCount);
    }

    @Override public String getName() {
        return "PatternKeySelector";
    }

}

