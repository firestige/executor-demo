package xyz.firestige.infrastructure.redis.renewal.filter;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalFilter;

import java.util.Collection;
import java.util.Map;

/**
 * 直通过滤器（默认实现）
 * <p>不做任何过滤和处理
 */
public class PassThroughFilter implements RenewalFilter {

    @Override
    public Collection<String> beforeRenewal(Collection<String> keys, long ttlSeconds) {
        return keys;
    }

    @Override
    public void afterRenewal(Map<String, Boolean> results) {
        // no-op
    }

    @Override
    public String getName() {
        return "PassThrough";
    }
}

