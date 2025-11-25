package xyz.firestige.redis.ack.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.redis.ack.api.AckMetricsRecorder;
import xyz.firestige.redis.ack.api.AckResult;
import xyz.firestige.redis.ack.api.HttpClient;
import xyz.firestige.redis.ack.api.PubSubStageBuilder;
import xyz.firestige.redis.ack.api.RedisAckService;
import xyz.firestige.redis.ack.api.VerifyStageBuilder;
import xyz.firestige.redis.ack.api.WriteStageBuilder;
import xyz.firestige.redis.ack.core.PubSubStageBuilderImpl;
import xyz.firestige.redis.ack.core.VerifyStageBuilderImpl;
import xyz.firestige.redis.ack.core.WriteStageBuilderImpl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Redis ACK 服务默认实现
 *
 * @author AI
 * @since 1.0
 */
public class DefaultRedisAckService implements RedisAckService {

    private final RedisTemplate<String, String> redisTemplate;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AckMetricsRecorder metricsRecorder;
    private final ExecutorService executorService;

    public DefaultRedisAckService(RedisTemplate<String, String> redisTemplate,
                                  HttpClient httpClient,
                                  ObjectMapper objectMapper) {
        this(redisTemplate, httpClient, objectMapper, AckMetricsRecorder.noop(), null);
    }

    public DefaultRedisAckService(RedisTemplate<String, String> redisTemplate,
                                  HttpClient httpClient,
                                  ObjectMapper objectMapper,
                                  AckMetricsRecorder metricsRecorder) {
        this(redisTemplate, httpClient, objectMapper, metricsRecorder, null);
    }

    public DefaultRedisAckService(RedisTemplate<String, String> redisTemplate,
                                  HttpClient httpClient,
                                  ObjectMapper objectMapper,
                                  AckMetricsRecorder metricsRecorder,
                                  ExecutorService executorService) {
        this.redisTemplate = redisTemplate;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.metricsRecorder = metricsRecorder != null ? metricsRecorder : AckMetricsRecorder.noop();
        this.executorService = executorService;
    }

    @Override
    public WriteStageBuilder write() {
        return new InstrumentedWriteStageBuilder(redisTemplate, httpClient, objectMapper, metricsRecorder, executorService);
    }

    /**
     * 带指标的 WriteStageBuilder 包装实现
     */
    static class InstrumentedWriteStageBuilder extends WriteStageBuilderImpl {
        private final AckMetricsRecorder metricsRecorder;

        InstrumentedWriteStageBuilder(RedisTemplate<String, String> redisTemplate,
                                      HttpClient httpClient,
                                      ObjectMapper objectMapper,
                                      AckMetricsRecorder metricsRecorder,
                                      ExecutorService executorService) {
            super(redisTemplate, httpClient, objectMapper, metricsRecorder, executorService);
            this.metricsRecorder = metricsRecorder;
        }

        @Override
        public PubSubStageBuilder andPublish() {
            PubSubStageBuilder delegate = super.andPublish();
            return new InstrumentedPubSubStageBuilder((PubSubStageBuilderImpl) delegate, metricsRecorder);
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
