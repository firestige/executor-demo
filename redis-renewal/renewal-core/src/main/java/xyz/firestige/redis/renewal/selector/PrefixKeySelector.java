package xyz.firestige.redis.renewal.selector;

import xyz.firestige.infrastructure.redis.renewal.api.KeySelector;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.RedisClient;

import java.util.Collection;
import java.util.Objects;

/**
 * 前缀扫描选择器
 * <p>扫描指定前缀的所有 Key
 */
public class PrefixKeySelector implements KeySelector {
    private final String prefix;
    private final RedisClient client;
    private final int scanCount;

    public PrefixKeySelector(String prefix, RedisClient client, int scanCount) {
        this.prefix = Objects.requireNonNull(prefix);
        this.client = Objects.requireNonNull(client);
        this.scanCount = scanCount;
    }

    public PrefixKeySelector(String prefix, RedisClient client) {
        this(prefix, client, 100);
    }

    @Override
    public Collection<String> selectKeys(RenewalContext context) {
        return client.scan(prefix + "*", scanCount);
    }

    @Override
    public String getName() {
        return "PrefixKeySelector";
    }
}

