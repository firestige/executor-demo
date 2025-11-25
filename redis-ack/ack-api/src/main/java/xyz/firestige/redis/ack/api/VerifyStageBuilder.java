package xyz.firestige.redis.ack.api;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Verify 阶段构建器
 * <p>
 * 负责配置端点验证和重试策略
 *
 * @author AI
 * @since 1.0
 */
public interface VerifyStageBuilder {

    // ========== 端点配置 ==========

    /**
     * 使用 HTTP GET 查询端点
     *
     * @param url 端点 URL
     * @return this
     */
    VerifyStageBuilder httpGet(String url);

    /**
     * 使用 HTTP POST 查询端点
     *
     * @param url 端点 URL
     * @param bodyBuilder 请求体构建函数，接收 footprint
     * @return this
     */
    VerifyStageBuilder httpPost(String url, Function<String, Object> bodyBuilder);

    /**
     * 使用自定义端点实现
     *
     * @param endpoint 端点实现
     * @return this
     */
    VerifyStageBuilder endpoint(AckEndpoint endpoint);

    // ========== Footprint 提取 ==========

    /**
     * 使用 JsonPath 从响应中提取 footprint
     *
     * @param jsonPath 例如 "$.currentVersion"
     * @return this
     */
    VerifyStageBuilder extractJson(String jsonPath);

    /**
     * 使用正则表达式从响应中提取 footprint
     *
     * @param pattern 正则表达式
     * @return this
     */
    VerifyStageBuilder extractRegex(String pattern);

    /**
     * 使用自定义函数提取 footprint
     *
     * @param extractor 提取函数，接收响应字符串
     * @return this
     */
    VerifyStageBuilder extractWith(Function<String, String> extractor);

    // ========== 重试策略 ==========

    /**
     * 使用自定义重试策略
     *
     * @param strategy 重试策略
     * @return this
     */
    VerifyStageBuilder retry(RetryStrategy strategy);

    /**
     * 使用固定延迟重试
     *
     * @param maxAttempts 最大尝试次数
     * @param delay 每次重试间隔
     * @return this
     */
    VerifyStageBuilder retryFixedDelay(int maxAttempts, Duration delay);

    /**
     * 使用指数退避重试
     *
     * @param maxAttempts 最大尝试次数
     * @param initialDelay 初始延迟
     * @param multiplier 倍增因子
     * @return this
     */
    VerifyStageBuilder retryExponential(int maxAttempts, Duration initialDelay, double multiplier);

    /**
     * 使用自定义重试决策函数
     *
     * @param retryDecider 决策函数 (attempt, error) -> nextDelay (null 表示停止)
     * @return this
     */
    VerifyStageBuilder retryCustom(BiFunction<Integer, Throwable, Duration> retryDecider);

    // ========== 超时配置 ==========

    /**
     * 设置整体超时时间（从开始执行到最终结果）
     *
     * @param timeout 超时时间
     * @return this
     */
    VerifyStageBuilder timeout(Duration timeout);

    // ========== 执行 ==========

    /**
     * 同步执行完整流程：Write → Pub/Sub → Verify
     * 阻塞等待验证结果
     *
     * @return ACK 结果
     * @throws xyz.firestige.infrastructure.redis.ack.exception.AckTimeoutException 超时
     * @throws xyz.firestige.infrastructure.redis.ack.exception.AckExecutionException 执行异常
     */
    AckResult executeAndWait();

    /**
     * 异步执行完整流程：Write → Pub/Sub → Verify
     * 立即返回 Future
     *
     * @return 异步 ACK 结果
     */
    CompletableFuture<AckResult> executeAsync();
}

