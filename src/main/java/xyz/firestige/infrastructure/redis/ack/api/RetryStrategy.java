package xyz.firestige.infrastructure.redis.ack.api;

import java.time.Duration;

/**
 * 重试策略接口
 * <p>
 * 用于决定验证失败后的重试行为
 *
 * @author AI
 * @since 1.0
 */
@FunctionalInterface
public interface RetryStrategy {

    /**
     * 决定下次重试的延迟时间
     *
     * @param attempt 当前尝试次数（从 1 开始）
     * @param lastError 上次错误（可能为 null）
     * @param context ACK 上下文
     * @return 延迟时间，null 表示停止重试
     */
    Duration nextDelay(int attempt, Throwable lastError, AckContext context);
}

