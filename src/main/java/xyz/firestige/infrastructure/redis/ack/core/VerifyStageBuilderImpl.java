package xyz.firestige.infrastructure.redis.ack.core;

import xyz.firestige.infrastructure.redis.ack.api.*;
import xyz.firestige.infrastructure.redis.ack.endpoint.HttpGetEndpoint;
import xyz.firestige.infrastructure.redis.ack.endpoint.HttpPostEndpoint;
import xyz.firestige.infrastructure.redis.ack.extractor.FunctionFootprintExtractor;
import xyz.firestige.infrastructure.redis.ack.extractor.JsonFieldExtractor;
import xyz.firestige.infrastructure.redis.ack.extractor.RegexFootprintExtractor;
import xyz.firestige.infrastructure.redis.ack.retry.FixedDelayRetryStrategy;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Verify 阶段构建器实现
 *
 * @author AI
 * @since 1.0
 */
public class VerifyStageBuilderImpl implements VerifyStageBuilder {

    private final WriteStageBuilderImpl writeStage;
    private final PubSubStageBuilderImpl pubSubStage;

    // Verify 配置
    private AckEndpoint endpoint;
    private Function<String, String> responseExtractor;
    private RetryStrategy retryStrategy;
    private Duration timeout = Duration.ofSeconds(60); // 默认超时

    public VerifyStageBuilderImpl(WriteStageBuilderImpl writeStage, PubSubStageBuilderImpl pubSubStage) {
        this.writeStage = writeStage;
        this.pubSubStage = pubSubStage;
    }

    @Override
    public VerifyStageBuilder httpGet(String url) {
        this.endpoint = new HttpGetEndpoint(url, writeStage.getRestTemplate());
        return this;
    }

    @Override
    public VerifyStageBuilder httpPost(String url, Function<String, Object> bodyBuilder) {
        this.endpoint = new HttpPostEndpoint(
            url, bodyBuilder,
            writeStage.getRestTemplate(),
            writeStage.getObjectMapper()
        );
        return this;
    }

    @Override
    public VerifyStageBuilder endpoint(AckEndpoint endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    @Override
    public VerifyStageBuilder extractJson(String jsonPath) {
        this.responseExtractor = response -> {
            FootprintExtractor extractor = new JsonFieldExtractor(jsonPath, writeStage.getObjectMapper());
            return extractor.extract(response);
        };
        return this;
    }

    @Override
    public VerifyStageBuilder extractRegex(String pattern) {
        this.responseExtractor = response -> new RegexFootprintExtractor(pattern).extract(response);
        return this;
    }

    @Override
    public VerifyStageBuilder extractWith(Function<String, String> extractor) {
        this.responseExtractor = extractor;
        return this;
    }

    @Override
    public VerifyStageBuilder retry(RetryStrategy strategy) {
        this.retryStrategy = strategy;
        return this;
    }

    @Override
    public VerifyStageBuilder retryFixedDelay(int maxAttempts, Duration delay) {
        this.retryStrategy = new FixedDelayRetryStrategy(maxAttempts, delay);
        return this;
    }

    @Override
    public VerifyStageBuilder retryExponential(int maxAttempts, Duration initialDelay, double multiplier) {
        this.retryStrategy = new xyz.firestige.infrastructure.redis.ack.retry.ExponentialBackoffRetryStrategy(
            maxAttempts, initialDelay, multiplier, Duration.ofSeconds(30)
        );
        return this;
    }

    @Override
    public VerifyStageBuilder retryCustom(BiFunction<Integer, Throwable, Duration> retryDecider) {
        this.retryStrategy = (attempt, error, ctx) -> retryDecider.apply(attempt, error);
        return this;
    }

    @Override
    public VerifyStageBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public AckResult executeAndWait() {
        validate();

        // 构建 AckTask
        AckTask task = buildTask();

        // 执行
        AckExecutor executor = new AckExecutor(writeStage.getRedisTemplate());
        return executor.execute(task);
    }

    @Override
    public CompletableFuture<AckResult> executeAsync() {
        validate();

        AckTask task = buildTask();
        AckExecutor executor = new AckExecutor(writeStage.getRedisTemplate());

        return CompletableFuture.supplyAsync(() -> executor.execute(task));
    }

    private void validate() {
        if (endpoint == null) {
            throw new IllegalStateException("endpoint is required");
        }
        if (responseExtractor == null) {
            throw new IllegalStateException("response extractor is required");
        }
        if (retryStrategy == null) {
            throw new IllegalStateException("retry strategy is required");
        }
    }

    private AckTask buildTask() {
        return new AckTask(
            UUID.randomUUID().toString(),
            writeStage.getKey(),
            writeStage.getField(),
            writeStage.getValue(),
            writeStage.getTtl(),
            writeStage.getOperation(),
            writeStage.getFootprintExtractor(),
            pubSubStage.getTopic(),
            pubSubStage.getMessageBuilder(),
            endpoint,
            responseExtractor,
            retryStrategy,
            timeout,
            writeStage.getZsetScore(),
            writeStage.getRedisTemplate()
        );
    }

    // ===== 新增内部状态访问器 (供包装/装饰使用) =====
    public WriteStageBuilderImpl getWriteStage() { return writeStage; }
    public PubSubStageBuilderImpl getPubSubStage() { return pubSubStage; }
    public AckEndpoint getEndpoint() { return endpoint; }
    public Function<String, String> getResponseExtractor() { return responseExtractor; }
    public RetryStrategy getRetryStrategy() { return retryStrategy; }
    public Duration getTimeout() { return timeout; }
}
