package xyz.firestige.redis.ack.spring.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import xyz.firestige.redis.ack.api.RedisClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Spring StringRedisTemplate 的 RedisClient 实现
 *
 * @author AI
 * @since 1.0
 */
public class SpringRedisClient implements RedisClient {

    private final StringRedisTemplate redisTemplate;

    public SpringRedisClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void setWithTtl(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void hset(String key, String field, String value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    @Override
    public void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void lpush(String key, String value) {
        redisTemplate.opsForList().leftPush(key, value);
    }

    @Override
    public void sadd(String key, String value) {
        redisTemplate.opsForSet().add(key, value);
    }

    @Override
    public void zadd(String key, String value, double score) {
        redisTemplate.opsForZSet().add(key, value, score);
    }

    @Override
    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }
}

