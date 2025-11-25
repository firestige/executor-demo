package xyz.firestige.redis.ack.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.firestige.redis.ack.api.AckMetricsRecorder;
import xyz.firestige.redis.ack.api.FootprintExtractor;
import xyz.firestige.redis.ack.api.HttpClient;
import xyz.firestige.redis.ack.api.PubSubStageBuilder;
import xyz.firestige.redis.ack.api.RedisClient;
import xyz.firestige.redis.ack.api.RedisOperation;
import xyz.firestige.redis.ack.api.WriteStageBuilder;
import xyz.firestige.redis.ack.extractor.FunctionFootprintExtractor;
import xyz.firestige.redis.ack.extractor.JsonFieldExtractor;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Write 阶段构建器实现
 *
 * @author AI
 * @since 1.0
 */
public class WriteStageBuilderImpl implements WriteStageBuilder {

    private final RedisClient redisClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AckMetricsRecorder metricsRecorder;
    private final ExecutorService executorService; // 用于并发验证

    // Write 配置
    private String key;
    private String field;  // Hash field
    private Object value;
    private Duration ttl;
    private RedisOperation operation;
    private Double zsetScore; // 仅当 ZADD 时使用

    // Footprint 配置
    private FootprintExtractor footprintExtractor;

    public WriteStageBuilderImpl(RedisClient redisClient,
                                 HttpClient httpClient,
                                 ObjectMapper objectMapper) {
        this(redisClient, httpClient, objectMapper, AckMetricsRecorder.noop(), null);
    }

    public WriteStageBuilderImpl(RedisClient redisClient,
                                 HttpClient httpClient,
                                 ObjectMapper objectMapper,
                                 AckMetricsRecorder metricsRecorder) {
        this(redisClient, httpClient, objectMapper, metricsRecorder, null);
    }

    public WriteStageBuilderImpl(RedisClient redisClient,
                                 HttpClient httpClient,
                                 ObjectMapper objectMapper,
                                 AckMetricsRecorder metricsRecorder,
                                 ExecutorService executorService) {
        this.redisClient = redisClient;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.metricsRecorder = metricsRecorder;
        this.executorService = executorService;
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
    RedisClient getRedisClient() { return redisClient; }
    HttpClient getHttpClient() { return httpClient; }
    ObjectMapper getObjectMapper() { return objectMapper; }
    ExecutorService getExecutorService() { return executorService; }
    public WriteStageBuilder zsetScore(double score) { this.zsetScore = score; return this; }
    Double getZsetScore() { return zsetScore; }
    AckMetricsRecorder getMetricsRecorder() { return metricsRecorder; }
}
