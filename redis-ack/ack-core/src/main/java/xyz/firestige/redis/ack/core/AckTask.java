package xyz.firestige.redis.ack.core;

import xyz.firestige.redis.ack.api.AckEndpoint;
import xyz.firestige.redis.ack.api.FootprintExtractor;
import xyz.firestige.redis.ack.api.RedisClient;
import xyz.firestige.redis.ack.api.RedisOperation;
import xyz.firestige.redis.ack.api.RetryStrategy;
import xyz.firestige.redis.ack.api.VersionTagExtractor;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * ACK 任务封装
 * <p>
 * 包含完整的 Write + Pub/Sub + Verify 配置
 *
 * <p><b>版本 2.0 更新</b>:
 * <ul>
 *   <li>支持多字段模式（fields + multiFieldMode）</li>
 *   <li>支持字段级 versionTag 提取</li>
 * </ul>
 *
 * @author AI
 * @since 1.0
 */
public class AckTask {

    private final String taskId;

    // Write 配置（单字段模式）
    private final String key;
    private final String field;
    private final Object value;
    private final Duration ttl;
    private final RedisOperation operation;
    private final FootprintExtractor footprintExtractor;

    // Write 配置（多字段模式 - Phase 2 新增）
    private final Map<String, Object> fields;
    private final boolean multiFieldMode;
    private final String versionTagSourceField;
    private final VersionTagExtractor fieldLevelExtractor;
    private final Function<Map<String, Object>, String> fieldsLevelExtractor;

    // Pub/Sub 配置
    private final String topic;
    private final Function<Object, String> messageBuilder;

    // Verify 配置
    private final AckEndpoint endpoint;
    private final Function<String, String> responseExtractor;
    private final RetryStrategy retryStrategy;
    private final Duration timeout;

    private final Double zsetScore; // ZADD 分数
    private final RedisClient redisClient; // Redis 客户端抽象

    /**
     * 完整构造函数（支持多字段模式）
     *
     * @since 2.0
     */
    public AckTask(String taskId,
                   String key, String field, Object value, Duration ttl, RedisOperation operation,
                   FootprintExtractor footprintExtractor,
                   String topic, Function<Object, String> messageBuilder,
                   AckEndpoint endpoint, Function<String, String> responseExtractor,
                   RetryStrategy retryStrategy, Duration timeout,
                   Double zsetScore,
                   RedisClient redisClient,
                   // 多字段模式参数（Phase 2 新增）
                   Map<String, Object> fields,
                   boolean multiFieldMode,
                   String versionTagSourceField,
                   VersionTagExtractor fieldLevelExtractor,
                   Function<Map<String, Object>, String> fieldsLevelExtractor) {
        this.taskId = taskId;
        this.key = key;
        this.field = field;
        this.value = value;
        this.ttl = ttl;
        this.operation = operation;
        this.footprintExtractor = footprintExtractor;
        this.topic = topic;
        this.messageBuilder = messageBuilder;
        this.endpoint = endpoint;
        this.responseExtractor = responseExtractor;
        this.retryStrategy = retryStrategy;
        this.timeout = timeout;
        this.zsetScore = zsetScore;
        this.redisClient = redisClient;
        // 多字段模式
        this.fields = fields;
        this.multiFieldMode = multiFieldMode;
        this.versionTagSourceField = versionTagSourceField;
        this.fieldLevelExtractor = fieldLevelExtractor;
        this.fieldsLevelExtractor = fieldsLevelExtractor;
    }

    /**
     * 兼容构造函数（单字段模式）
     *
     * @deprecated 使用新构造函数支持多字段模式
     */
    @Deprecated
    public AckTask(String taskId,
                   String key, String field, Object value, Duration ttl, RedisOperation operation,
                   FootprintExtractor footprintExtractor,
                   String topic, Function<Object, String> messageBuilder,
                   AckEndpoint endpoint, Function<String, String> responseExtractor,
                   RetryStrategy retryStrategy, Duration timeout,
                   Double zsetScore,
                   RedisClient redisClient) {
        this(taskId, key, field, value, ttl, operation, footprintExtractor,
             topic, messageBuilder, endpoint, responseExtractor, retryStrategy, timeout,
             zsetScore, redisClient,
             null, false, null, null, null);
    }

    // Getters (单字段模式)

    public String getTaskId() { return taskId; }
    public String getKey() { return key; }
    public String getField() { return field; }
    public Object getValue() { return value; }
    public Duration getTtl() { return ttl; }
    public RedisOperation getOperation() { return operation; }
    public FootprintExtractor getFootprintExtractor() { return footprintExtractor; }
    public String getTopic() { return topic; }
    public Function<Object, String> getMessageBuilder() { return messageBuilder; }
    public AckEndpoint getEndpoint() { return endpoint; }
    public Function<String, String> getResponseExtractor() { return responseExtractor; }
    public RetryStrategy getRetryStrategy() { return retryStrategy; }
    public Duration getTimeout() { return timeout; }
    Double getZsetScore() { return zsetScore; }
    RedisClient getRedisClient() { return redisClient; }

    // Getters (多字段模式 - Phase 2 新增)

    public Map<String, Object> getFields() { return fields; }
    public boolean isMultiFieldMode() { return multiFieldMode; }
    public String getVersionTagSourceField() { return versionTagSourceField; }
    public VersionTagExtractor getFieldLevelExtractor() { return fieldLevelExtractor; }
    public Function<Map<String, Object>, String> getFieldsLevelExtractor() { return fieldsLevelExtractor; }
}
