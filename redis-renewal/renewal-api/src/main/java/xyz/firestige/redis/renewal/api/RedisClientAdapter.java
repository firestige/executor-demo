package xyz.firestige.redis.renewal.api;

import java.time.Duration;
import java.util.Set;

public interface RedisClientAdapter {
    boolean expire(String key, Duration ttl);
    boolean exists(String key);
    Duration ttl(String key);
    Set<String> keys(String pattern);
}
