package xyz.firestige.deploy.redis;

import java.time.Duration;

/**
 * Thin abstraction over Redis operations we need, so we can swap client impls.
 */
public interface RedisClient {
    void set(String key, byte[] value, Duration ttl);
    byte[] get(String key);
    void del(String key);
}

