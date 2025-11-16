package xyz.firestige.executor.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

/**
 * Spring Data Redis based implementation; delegates to RedisTemplate.
 */
public class SpringDataRedisClient implements RedisClient {

    private final RedisTemplate<String, byte[]> template;

    public SpringDataRedisClient(RedisTemplate<String, byte[]> template) {
        this.template = template;
    }

    @Override
    public void set(String key, byte[] value, Duration ttl) {
        ValueOperations<String, byte[]> ops = template.opsForValue();
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            ops.set(key, value, ttl);
        } else {
            ops.set(key, value);
        }
    }

    @Override
    public byte[] get(String key) {
        ValueOperations<String, byte[]> ops = template.opsForValue();
        return ops.get(key);
    }

    @Override
    public void del(String key) {
        template.delete(key);
    }
}

