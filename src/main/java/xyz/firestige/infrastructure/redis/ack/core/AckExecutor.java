package xyz.firestige.infrastructure.redis.ack.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.infrastructure.redis.ack.api.AckContext;
import xyz.firestige.infrastructure.redis.ack.api.AckResult;
import xyz.firestige.infrastructure.redis.ack.api.RedisOperation;
import xyz.firestige.infrastructure.redis.ack.exception.AckExecutionException;
import xyz.firestige.infrastructure.redis.ack.exception.AckTimeoutException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * ACK 执行器
 * <p>
 * 协调 Write → Pub/Sub → Verify 三阶段的执行
 *
 * @author AI
 * @since 1.0
 */
public class AckExecutor {

    private static final Logger log = LoggerFactory.getLogger(AckExecutor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RedisTemplate<String, String> redisTemplate;

    public AckExecutor(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 执行完整的 ACK 流程
     */
    public AckResult execute(AckTask task) {
        Instant startTime = Instant.now();
        AckContext context = new AckContext(task.getTaskId());

        try {
            log.debug("[ACK] 开始执行任务: {}", task.getTaskId());

            // 1️⃣ Write Phase
            log.debug("[ACK] Phase 1: Write to Redis");
            String footprint = writeToRedis(task, context);
            context.setFootprint(footprint);
            log.debug("[ACK] Footprint extracted: {}", footprint);

            // 2️⃣ Pub/Sub Phase
            log.debug("[ACK] Phase 2: Publish to Pub/Sub");
            publishNotification(task, context);

            // 3️⃣ Verify Phase
            log.debug("[ACK] Phase 3: Verify with retry");
            String actualFootprint = verifyWithRetry(task, context);

            // 比对
            Duration elapsed = Duration.between(startTime, Instant.now());
            int attempts = (int) context.getAttribute("attempts");

            if (footprint.equals(actualFootprint)) {
                log.info("[ACK] 验证成功: taskId={}, footprint={}, attempts={}, elapsed={}ms",
                        task.getTaskId(), footprint, attempts, elapsed.toMillis());
                return AckResult.success(footprint, actualFootprint, attempts, elapsed);
            } else {
                log.warn("[ACK] Footprint 不匹配: expected={}, actual={}", footprint, actualFootprint);
                return AckResult.mismatch(footprint, actualFootprint, attempts, elapsed);
            }

        } catch (AckTimeoutException e) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            int attempts = (int) context.getAttribute("attempts");
            log.error("[ACK] 验证超时: taskId={}, attempts={}", task.getTaskId(), attempts);
            return AckResult.timeout(context.getFootprint(), attempts, elapsed);

        } catch (Exception e) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            int attempts = (int) context.getAttribute("attempts");
            log.error("[ACK] 执行失败: taskId=" + task.getTaskId(), e);
            return AckResult.error(context.getFootprint(), attempts, elapsed, e);
        }
    }

    /**
     * 写入 Redis
     */
    private String writeToRedis(AckTask task, AckContext context) {
        try {
            // 提取 footprint
            String footprint = task.getFootprintExtractor().extract(task.getValue());

            // 序列化 value
            String valueStr = serializeValue(task.getValue());

            // 根据操作类型写入
            RedisOperation operation = task.getOperation();
            if (operation == RedisOperation.HSET) {
                redisTemplate.opsForHash().put(task.getKey(), task.getField(), valueStr);
                log.debug("[ACK] HSET {} {} {}", task.getKey(), task.getField(), valueStr.substring(0, Math.min(50, valueStr.length())));
            } else if (operation == RedisOperation.SET) {
                redisTemplate.opsForValue().set(task.getKey(), valueStr);
                log.debug("[ACK] SET {} {}", task.getKey(), valueStr.substring(0, Math.min(50, valueStr.length())));
            } else {
                throw new UnsupportedOperationException("Operation not yet supported: " + operation);
            }

            // 设置 TTL（如果有）
            if (task.getTtl() != null) {
                redisTemplate.expire(task.getKey(), task.getTtl().toMillis(), TimeUnit.MILLISECONDS);
                log.debug("[ACK] Set TTL: {} ms", task.getTtl().toMillis());
            }

            return footprint;

        } catch (Exception e) {
            throw new AckExecutionException("Failed to write to Redis", e);
        }
    }

    /**
     * 发布 Pub/Sub 通知
     */
    private void publishNotification(AckTask task, AckContext context) {
        try {
            String message = task.getMessageBuilder().apply(task.getValue());
            redisTemplate.convertAndSend(task.getTopic(), message);
            log.debug("[ACK] Published to topic {}: {}", task.getTopic(), message);

        } catch (Exception e) {
            // Pub/Sub 失败不中断流程，仅记录警告
            log.warn("[ACK] Pub/Sub failed (continuing): topic={}, error={}",
                    task.getTopic(), e.getMessage());
        }
    }

    /**
     * 带重试的验证
     */
    private String verifyWithRetry(AckTask task, AckContext context) {
        Instant deadline = Instant.now().plus(task.getTimeout());
        int attempt = 0;
        Throwable lastError = null;

        while (Instant.now().isBefore(deadline)) {
            attempt++;
            context.setAttribute("attempts", attempt);

            try {
                // 查询端点
                String response = task.getEndpoint().query(context);
                log.debug("[ACK] Endpoint response (attempt {}): {}", attempt,
                         response.substring(0, Math.min(100, response.length())));

                // 提取 footprint
                String actualFootprint = task.getResponseExtractor().apply(response);
                log.debug("[ACK] Extracted footprint: {}", actualFootprint);

                return actualFootprint;

            } catch (Exception e) {
                lastError = e;
                log.debug("[ACK] Verify attempt {} failed: {}", attempt, e.getMessage());

                // 询问重试策略
                Duration nextDelay = task.getRetryStrategy().nextDelay(attempt, e, context);

                if (nextDelay == null) {
                    log.warn("[ACK] Retry strategy stopped after {} attempts", attempt);
                    throw new AckExecutionException("Verification failed after " + attempt + " attempts", e);
                }

                // 检查是否会超时
                if (Instant.now().plus(nextDelay).isAfter(deadline)) {
                    log.warn("[ACK] Next retry would exceed timeout");
                    throw new AckTimeoutException("Timeout after " + attempt + " attempts");
                }

                // 等待后重试
                sleep(nextDelay);
            }
        }

        throw new AckTimeoutException("Timeout after " + attempt + " attempts");
    }

    /**
     * 序列化值为字符串
     */
    private String serializeValue(Object value) throws Exception {
        if (value instanceof String) {
            return (String) value;
        }
        return objectMapper.writeValueAsString(value);
    }

    /**
     * 休眠
     */
    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AckExecutionException("Sleep interrupted", e);
        }
    }
}

