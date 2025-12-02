package xyz.firestige.redis.ack.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.firestige.redis.ack.api.AckMetricsRecorder;
import xyz.firestige.redis.ack.api.FootprintExtractor;
import xyz.firestige.redis.ack.api.HttpClient;
import xyz.firestige.redis.ack.api.PubSubStageBuilder;
import xyz.firestige.redis.ack.api.RedisClient;
import xyz.firestige.redis.ack.api.RedisOperation;
import xyz.firestige.redis.ack.api.WriteStageBuilder;
import xyz.firestige.redis.ack.api.HashFieldsBuilder;
import xyz.firestige.redis.ack.api.VersionTagExtractor;
import xyz.firestige.redis.ack.extractor.FunctionFootprintExtractor;
import xyz.firestige.redis.ack.extractor.FunctionVersionTagExtractor;
import xyz.firestige.redis.ack.extractor.JsonFieldExtractor;
import xyz.firestige.redis.ack.extractor.JsonFieldVersionTagExtractor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
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
    private final Executor executor; // 用于并发验证

    // Write 配置
    private String key;
    private String field;  // Hash field (单字段模式)
    private Object value;
    private Duration ttl;
    private RedisOperation operation;
    private Double zsetScore; // 仅当 ZADD 时使用

    // 多字段模式配置（Phase 2 新增）
    private Map<String, Object> fields;  // 多字段模式的 fields
    private String versionTagSourceField;  // 从哪个 field 提取 versionTag
    private VersionTagExtractor fieldLevelExtractor;  // field 级别提取器
    private Function<Map<String, Object>, String> fieldsLevelExtractor;  // 多 fields 组合提取器

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
                                 Executor executor) {
        this.redisClient = redisClient;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.metricsRecorder = metricsRecorder;
        this.executor = executor;
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
    public WriteStageBuilder versionTag(String fieldName) {
        // Wrap VersionTagExtractor as FootprintExtractor
        this.footprintExtractor = (value) -> new JsonFieldVersionTagExtractor(fieldName, objectMapper).extractTag(value);
        return this;
    }

    @Override
    public WriteStageBuilder versionTag(VersionTagExtractor extractor) {
        this.footprintExtractor = (value) -> extractor.extractTag(value);
        return this;
    }

    @Override
    public WriteStageBuilder versionTag(Function<Object, String> calculator) {
        this.footprintExtractor = (value) -> new FunctionVersionTagExtractor(calculator).extractTag(value);
        return this;
    }

    @Override
    public WriteStageBuilder versionTagFromPath(String jsonPath) {
        this.footprintExtractor = (value) -> new JsonFieldVersionTagExtractor(jsonPath, objectMapper).extractTag(value);
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
    Executor getExecutor() { return executor; }
    public WriteStageBuilder zsetScore(double score) { this.zsetScore = score; return this; }
    Double getZsetScore() { return zsetScore; }
    AckMetricsRecorder getMetricsRecorder() { return metricsRecorder; }

    @Override
    public HashFieldsBuilder hashKey(String key) {
        this.key = key;
        this.fields = new java.util.LinkedHashMap<>();
        this.operation = RedisOperation.HSET;
        return new HashFieldsBuilderImpl();
    }

    /**
     * HashFieldsBuilder 内部实现
     * <p>
     * 支持多字段原子写入和灵活的 versionTag 提取
     *
     * @since 2.0
     */
    private class HashFieldsBuilderImpl implements HashFieldsBuilder {

        @Override
        public HashFieldsBuilder field(String field, Object value) {
            fields.put(field, value);
            return this;
        }

        @Override
        public HashFieldsBuilder fields(java.util.Map<String, Object> newFields) {
            if (newFields != null) {
                fields.putAll(newFields);
            }
            return this;
        }

        @Override
        public WriteStageBuilder versionTagFromField(String fieldName, VersionTagExtractor extractor) {
            versionTagSourceField = fieldName;
            fieldLevelExtractor = extractor;
            // 包装为 FootprintExtractor（从指定 field 提取）
            footprintExtractor = (ignored) -> {
                Object fieldValue = fields.get(fieldName);
                if (fieldValue == null) {
                    throw new IllegalStateException("Field not found: " + fieldName);
                }
                return extractor.extractTag(fieldValue);
            };
            return WriteStageBuilderImpl.this;
        }

        @Override
        public WriteStageBuilder versionTagFromField(String fieldName, String jsonPath) {
            versionTagSourceField = fieldName;
            fieldLevelExtractor = new JsonFieldVersionTagExtractor(jsonPath, objectMapper);
            // 包装为 FootprintExtractor
            footprintExtractor = (ignored) -> {
                Object fieldValue = fields.get(fieldName);
                if (fieldValue == null) {
                    throw new IllegalStateException("Field not found: " + fieldName);
                }
                return fieldLevelExtractor.extractTag(fieldValue);
            };
            return WriteStageBuilderImpl.this;
        }

        @Override
        public WriteStageBuilder versionTagFromFields(Function<java.util.Map<String, Object>, String> extractor) {
            fieldsLevelExtractor = extractor;
            // 包装为 FootprintExtractor（从整个 fields Map 提取）
            footprintExtractor = (ignored) -> extractor.apply(fields);
            return WriteStageBuilderImpl.this;
        }
    }

    // Getters (新增多字段模式相关)
    boolean isMultiFieldMode() {
        return fields != null && !fields.isEmpty();
    }

    java.util.Map<String, Object> getFields() {
        return fields;
    }

    String getVersionTagSourceField() {
        return versionTagSourceField;
    }

    VersionTagExtractor getFieldLevelExtractor() {
        return fieldLevelExtractor;
    }

    Function<java.util.Map<String, Object>, String> getFieldsLevelExtractor() {
        return fieldsLevelExtractor;
    }
}
