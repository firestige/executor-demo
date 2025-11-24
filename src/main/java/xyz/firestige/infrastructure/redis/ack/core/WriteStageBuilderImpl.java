package xyz.firestige.infrastructure.redis.ack.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.infrastructure.redis.ack.api.*;
import xyz.firestige.infrastructure.redis.ack.endpoint.HttpGetEndpoint;
import xyz.firestige.infrastructure.redis.ack.endpoint.HttpPostEndpoint;
import xyz.firestige.infrastructure.redis.ack.exception.AckExecutionException;
import xyz.firestige.infrastructure.redis.ack.extractor.FunctionFootprintExtractor;
import xyz.firestige.infrastructure.redis.ack.extractor.JsonFieldExtractor;
import xyz.firestige.infrastructure.redis.ack.retry.FixedDelayRetryStrategy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Write 阶段构建器实现
 *
 * @author AI
 * @since 1.0
 */
public class WriteStageBuilderImpl implements WriteStageBuilder {

    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Write 配置
    private String key;
    private String field;  // Hash field
    private Object value;
    private Duration ttl;
    private RedisOperation operation;

    // Footprint 配置
    private FootprintExtractor footprintExtractor;

    public WriteStageBuilderImpl(RedisTemplate<String, String> redisTemplate,
                                 RestTemplate restTemplate,
                                 ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public WriteStageBuilder key(String key) {
        this.key = key;
        return this;
    }

    @Override
    public WriteStageBuilder hashKey(String key, String field) {
        this.key = key;
        this.field = field;
        this.operation = RedisOperation.HSET;
        return this;
    }

    @Override
    public WriteStageBuilder value(Object value) {
        this.value = value;
        return this;
    }

    @Override
    public WriteStageBuilder ttl(Duration ttl) {
        this.ttl = ttl;
        return this;
    }

    @Override
    public WriteStageBuilder operation(RedisOperation operation) {
        this.operation = operation;
        return this;
    }

    @Override
    public WriteStageBuilder footprint(String fieldName) {
        this.footprintExtractor = new JsonFieldExtractor(fieldName, objectMapper);
        return this;
    }

    @Override
    public WriteStageBuilder footprint(FootprintExtractor extractor) {
        this.footprintExtractor = extractor;
        return this;
    }

    @Override
    public WriteStageBuilder footprint(Function<Object, String> calculator) {
        this.footprintExtractor = new FunctionFootprintExtractor(calculator);
        return this;
    }

    @Override
    public PubSubStageBuilder andPublish() {
        validate();
        return new PubSubStageBuilderImpl(this);
    }

    private void validate() {
        if (key == null) {
            throw new IllegalStateException("key is required");
        }
        if (value == null) {
            throw new IllegalStateException("value is required");
        }
        if (footprintExtractor == null) {
            throw new IllegalStateException("footprint is required");
        }
        // 如果是 HSET 但没有 field
        if (operation == RedisOperation.HSET && field == null) {
            throw new IllegalStateException("field is required for HSET operation");
        }
    }

    // Getters

    String getKey() { return key; }
    String getField() { return field; }
    Object getValue() { return value; }
    Duration getTtl() { return ttl; }
    RedisOperation getOperation() {
        if (operation == null) {
            return field != null ? RedisOperation.HSET : RedisOperation.SET;
        }
        return operation;
    }
    FootprintExtractor getFootprintExtractor() { return footprintExtractor; }
    RedisTemplate<String, String> getRedisTemplate() { return redisTemplate; }
    RestTemplate getRestTemplate() { return restTemplate; }
    ObjectMapper getObjectMapper() { return objectMapper; }
}

