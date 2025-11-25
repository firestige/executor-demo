package xyz.firestige.redis.ack.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.redis.ack.api.AckMetricsRecorder;
import xyz.firestige.redis.ack.api.AckResult;
import xyz.firestige.redis.ack.api.PubSubStageBuilder;
import xyz.firestige.redis.ack.api.RedisAckService;
import xyz.firestige.redis.ack.api.VerifyStageBuilder;
import xyz.firestige.redis.ack.api.WriteStageBuilder;

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
    private final AckMetricsRecorder metricsRecorder;

    public DefaultRedisAckService(RedisTemplate<String, String> redisTemplate,
                                  RestTemplate restTemplate,
                                  ObjectMapper objectMapper) {
        this(redisTemplate, restTemplate, objectMapper, AckMetricsRecorder.noop());
    }

    public DefaultRedisAckService(RedisTemplate<String, String> redisTemplate,
                                  RestTemplate restTemplate,
                                  ObjectMapper objectMapper,
                                  AckMetricsRecorder metricsRecorder) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.metricsRecorder = metricsRecorder != null ? metricsRecorder : AckMetricsRecorder.noop();
    }

    @Override
    public WriteStageBuilder write() {
        return new InstrumentedWriteStageBuilder(redisTemplate, restTemplate, objectMapper, metricsRecorder);
    }

    /**
     * 带指标的 WriteStageBuilder 包装实现
     */
    static class InstrumentedWriteStageBuilder extends WriteStageBuilderImpl {
        InstrumentedWriteStageBuilder(RedisTemplate<String, String> redisTemplate,
                                      RestTemplate restTemplate,
                                      ObjectMapper objectMapper,
                                      AckMetricsRecorder metricsRecorder) {
            super(redisTemplate, restTemplate, objectMapper, metricsRecorder);
        }

        @Override
        public PubSubStageBuilder andPublish() {
            PubSubStageBuilder delegate = super.andPublish();
            return new InstrumentedPubSubStageBuilder((PubSubStageBuilderImpl) delegate, getMetricsRecorder());
        }
    }

    /**
     * 带指标的 PubSubStageBuilder 包装
     */
    static class InstrumentedPubSubStageBuilder extends PubSubStageBuilderImpl {
        private final AckMetricsRecorder metricsRecorder;

        InstrumentedPubSubStageBuilder(PubSubStageBuilderImpl delegate, AckMetricsRecorder metricsRecorder) {
            super(delegate.getWriteStage());
            this.topic(delegate.getTopic());
            this.message(delegate.getMessageBuilder());
            this.metricsRecorder = metricsRecorder;
        }

        @Override
        public VerifyStageBuilder andVerify() {
            VerifyStageBuilder delegate = super.andVerify();
            return new InstrumentedVerifyStageBuilder((VerifyStageBuilderImpl) delegate, metricsRecorder);
        }
    }

    /**
     * 带指标的 VerifyStageBuilder 包装
     */
    static class InstrumentedVerifyStageBuilder extends VerifyStageBuilderImpl {
        private final AckMetricsRecorder metricsRecorder;

        InstrumentedVerifyStageBuilder(VerifyStageBuilderImpl delegate, AckMetricsRecorder metricsRecorder) {
            super(delegate.getWriteStage(), delegate.getPubSubStage());
            this.metricsRecorder = metricsRecorder;
            if (delegate.getEndpoint() != null) this.endpoint(delegate.getEndpoint());
            if (delegate.getResponseExtractor() != null) this.extractWith(delegate.getResponseExtractor());
            if (delegate.getRetryStrategy() != null) this.retry(delegate.getRetryStrategy());
            this.timeout(delegate.getTimeout());
        }

        @Override
        public AckResult executeAndWait() {
            AckResult result = super.executeAndWait();
            metricsRecorder.record(result);
            return result;
        }

        @Override
        public CompletableFuture<AckResult> executeAsync() {
            return super.executeAsync().thenApply(result -> {
                metricsRecorder.record(result);
                return result;
            });
        }
    }
}
