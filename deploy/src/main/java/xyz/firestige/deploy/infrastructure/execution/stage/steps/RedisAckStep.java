package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.stage.StageStep;
import xyz.firestige.redis.ack.api.AckResult;
import xyz.firestige.redis.ack.api.RedisAckService;
import xyz.firestige.redis.ack.exception.AckExecutionException;
import xyz.firestige.redis.ack.exception.AckTimeoutException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Redis ACK Step（Write + Pub/Sub + Verify 一体化）
 *
 * <p>职责：
 * <ul>
 *   <li>从 TaskRuntimeContext 提取 ACK 配置参数</li>
 *   <li>调用 RedisAckService 执行完整流程</li>
 *   <li>将 AckResult 放回 TaskRuntimeContext</li>
 *   <li>异常转换为 FailureInfo</li>
 * </ul>
 *
 * <p>输入参数（从 TaskRuntimeContext）：
 * <ul>
 *   <li>redisKey: String - Redis Hash Key</li>
 *   <li>redisField: String - Redis Hash Field</li>
 *   <li>redisValue: Map - 写入的值对象</li>
 *   <li>footprint: String - footprint 值（从 PlanVersion）</li>
 *   <li>pubsubTopic: String - Pub/Sub Topic</li>
 *   <li>pubsubMessage: String - Pub/Sub Message</li>
 *   <li>verifyUrls: List&lt;String&gt; - 健康检查 URL 列表</li>
 *   <li>verifyJsonPath: String - footprint 提取 JsonPath</li>
 *   <li>retryMaxAttempts: int - 最大重试次数</li>
 *   <li>retryDelay: Duration - 重试间隔</li>
 *   <li>timeout: Duration - 总超时时间</li>
 * </ul>
 *
 * <p>输出：
 * <ul>
 *   <li>ackResult: AckResult - ACK 执行结果</li>
 * </ul>
 *
 * @since RF-19-06
 */
public class RedisAckStep implements StageStep {

    private static final Logger log = LoggerFactory.getLogger(RedisAckStep.class);

    private final RedisAckService redisAckService;

    public RedisAckStep(RedisAckService redisAckService) {
        this.redisAckService = redisAckService;
    }

    @Override
    public String getStepName() {
        return "redis-ack";
    }

    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        try {
            // 1. 提取参数
            String redisKey = getRequired(ctx, "redisKey", String.class);
            String redisField = getRequired(ctx, "redisField", String.class);
            Map<String, Object> redisValue = getRequired(ctx, "redisValue", Map.class);
            Map<String, Object> metadata = getRequired(ctx, "metadata", Map.class);
            String versionTagPath = getRequired(ctx, "versionTagPath", String.class);
            String pubsubTopic = getRequired(ctx, "pubsubTopic", String.class);
            String pubsubMessage = getRequired(ctx, "pubsubMessage", String.class);
            List<String> verifyUrls = getRequired(ctx, "verifyUrls", List.class);
            String verifyJsonPath = getRequired(ctx, "verifyJsonPath", String.class);
            int retryMaxAttempts = getRequired(ctx, "retryMaxAttempts", Integer.class);
            Duration retryDelay = getRequired(ctx, "retryDelay", Duration.class);
            Duration timeout = getRequired(ctx, "timeout", Duration.class);

            log.info("开始执行 RedisAck: key={}, field={}, endpoints={}",
                redisKey, redisField, verifyUrls.size());

            // 2. 调用 RedisAckService
            AckResult result = redisAckService.write()
                    .hashKey(redisKey)
                    .field(redisField, redisValue)
                    .field("metadata", metadata)
                    .versionTagFromField("metadata", versionTagPath)// 直接使用 PlanVersion

                .andPublish()
                    .topic(pubsubTopic)
                    .message(pubsubMessage)

                .andVerify()
                    .httpGetMultiple(verifyUrls) // 多 URL 并发验证
                    .extractJson(verifyJsonPath)
                    .retryFixedDelay(retryMaxAttempts, retryDelay)
                    .timeout(timeout)

                .executeAndWait(); // 同步调用

            // 3. 放回结果
            ctx.addVariable("ackResult", result);

            log.info("RedisAck 执行完成: success={}, attempts={}, elapsed={}",
                result.isSuccess(), result.getAttempts(), result.getElapsed());

        } catch (AckTimeoutException e) {
            // 超时异常 - 可重试
            log.error("RedisAck 超时: {}", e.getMessage());
            FailureInfo failureInfo = FailureInfo.of(
                ErrorType.TIMEOUT_ERROR,
                "ACK 验证超时: " + e.getMessage(),
                true // retryable
            );
            ctx.addVariable("failureInfo", failureInfo);
            throw e;

        } catch (AckExecutionException e) {
            // 执行异常 - 不可重试
            log.error("RedisAck 执行失败: {}", e.getMessage());
            FailureInfo failureInfo = FailureInfo.of(
                ErrorType.SERVICE_UNAVAILABLE,
                "ACK 执行失败: " + e.getMessage(),
                false // not retryable
            );
            ctx.addVariable("failureInfo", failureInfo);
            throw e;

        } catch (Exception e) {
            // 未知异常 - 不可重试
            log.error("RedisAck 未知错误: {}", e.getMessage());
            FailureInfo failureInfo = FailureInfo.of(
                ErrorType.SYSTEM_ERROR,
                "RedisAck 未知错误: " + e.getMessage(),
                false
            );
            ctx.addVariable("failureInfo", failureInfo);
            throw e;
        }
    }

    private <T> T getRequired(TaskRuntimeContext ctx, String key, Class<T> clazz) {
        T value = ctx.getAdditionalData(key, clazz);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required in TaskRuntimeContext");
        }
        return value;
    }
}

