package xyz.firestige.infrastructure.redis.ack.core;

import xyz.firestige.infrastructure.redis.ack.api.*;

import java.time.Duration;
import java.util.function.Function;

/**
 * ACK 任务封装
 * <p>
 * 包含完整的 Write + Pub/Sub + Verify 配置
 *
 * @author AI
 * @since 1.0
 */
public class AckTask {

    private final String taskId;

    // Write 配置
    private final String key;
    private final String field;
    private final Object value;
    private final Duration ttl;
    private final RedisOperation operation;
    private final FootprintExtractor footprintExtractor;

    // Pub/Sub 配置
    private final String topic;
    private final Function<Object, String> messageBuilder;

    // Verify 配置
    private final AckEndpoint endpoint;
    private final Function<String, String> responseExtractor;
    private final RetryStrategy retryStrategy;
    private final Duration timeout;

    public AckTask(String taskId,
                   String key, String field, Object value, Duration ttl, RedisOperation operation,
                   FootprintExtractor footprintExtractor,
                   String topic, Function<Object, String> messageBuilder,
                   AckEndpoint endpoint, Function<String, String> responseExtractor,
                   RetryStrategy retryStrategy, Duration timeout) {
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
    }

    // Getters

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
}

