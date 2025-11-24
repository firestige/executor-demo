package xyz.firestige.infrastructure.redis.ack.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.infrastructure.redis.ack.api.PubSubStageBuilder;
import xyz.firestige.infrastructure.redis.ack.api.RedisAckService;
import xyz.firestige.infrastructure.redis.ack.api.VerifyStageBuilder;
import xyz.firestige.infrastructure.redis.ack.api.WriteStageBuilder;
import xyz.firestige.infrastructure.redis.ack.metrics.AckMetrics;
import xyz.firestige.infrastructure.redis.ack.api.AckResult;

import java.util.concurrent.CompletableFuture;

/**
 * Redis ACK 服务默认实现
 *
 * @author AI
 * @since 1.0
 */
public class DefaultRedisAckService implements RedisAckService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AckMetrics ackMetrics; // 可为空

    public DefaultRedisAckService(RedisTemplate<String, String> redisTemplate,
                                  RestTemplate restTemplate,
                                  ObjectMapper objectMapper) {
        this(redisTemplate, restTemplate, objectMapper, null);
    }

    public DefaultRedisAckService(RedisTemplate<String, String> redisTemplate,
                                  RestTemplate restTemplate,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.ackMetrics = meterRegistry != null ? new AckMetrics(meterRegistry) : null;
    }

    @Override
    public WriteStageBuilder write() {
        // 直接返回基础 builder，指标埋点下沉到包装的 Verify 执行阶段
        return new InstrumentedWriteStageBuilder(redisTemplate, restTemplate, objectMapper, ackMetrics);
    }

    /**
     * 带指标的 WriteStageBuilder 包装实现
     */
    static class InstrumentedWriteStageBuilder extends WriteStageBuilderImpl {
        InstrumentedWriteStageBuilder(RedisTemplate<String, String> redisTemplate,
                                      RestTemplate restTemplate,
                                      ObjectMapper objectMapper,
                                      AckMetrics metrics) {
            super(redisTemplate, restTemplate, objectMapper, metrics);
        }

        @Override
        public PubSubStageBuilder andPublish() {
            PubSubStageBuilder delegate = super.andPublish();
            return new InstrumentedPubSubStageBuilder((PubSubStageBuilderImpl) delegate, getAckMetrics());
        }
    }

    /**
     * 带指标的 PubSubStageBuilder 包装
     */
    static class InstrumentedPubSubStageBuilder extends PubSubStageBuilderImpl {
        private final AckMetrics metrics;
        InstrumentedPubSubStageBuilder(PubSubStageBuilderImpl delegate, AckMetrics metrics) {
            super(delegate.getWriteStage());
            // 复制必要状态
            this.topic(delegate.getTopic());
            this.message(delegate.getMessageBuilder());
            this.metrics = metrics;
        }

        @Override
        public VerifyStageBuilder andVerify() {
            VerifyStageBuilder delegate = super.andVerify();
            return new InstrumentedVerifyStageBuilder((VerifyStageBuilderImpl) delegate, metrics);
        }
    }

    /**
     * 带指标的 VerifyStageBuilder 包装
     */
    static class InstrumentedVerifyStageBuilder extends VerifyStageBuilderImpl {
        private final AckMetrics metrics;
        InstrumentedVerifyStageBuilder(VerifyStageBuilderImpl delegate, AckMetrics metrics) {
            super(delegate.getWriteStage(), delegate.getPubSubStage());
            this.metrics = metrics;
            // 复制配置
            if (delegate.getEndpoint() != null) this.endpoint(delegate.getEndpoint());
            if (delegate.getResponseExtractor() != null) this.extractWith(delegate.getResponseExtractor());
            if (delegate.getRetryStrategy() != null) this.retry(delegate.getRetryStrategy());
            this.timeout(delegate.getTimeout());
        }

        @Override
        public AckResult executeAndWait() {
            AckResult r = super.executeAndWait();
            if (metrics != null) metrics.record(r);
            return r;
        }

        @Override
        public CompletableFuture<AckResult> executeAsync() {
            return super.executeAsync().thenApply(r -> {
                if (metrics != null) metrics.record(r);
                return r;
            });
        }
    }
}
