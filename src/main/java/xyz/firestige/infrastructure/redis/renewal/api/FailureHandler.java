package xyz.firestige.infrastructure.redis.renewal.api;

import java.time.Duration;
import java.util.Collection;

/**
 * 失败处理器接口
 *
 * <p>处理续期失败的情况，决定如何应对失败。
 *
 * <h3>预置实现</h3>
 * <ul>
 *   <li>{@code LogAndContinueFailureHandler} - 记录日志并继续（默认）</li>
 * </ul>
 *
 * <h3>自定义示例</h3>
 * <pre>{@code
 * public class RetryFailureHandler implements FailureHandler {
 *     public FailureHandleResult handle(String taskId, Collection<String> keys, Throwable error) {
 *         if (retryCount < maxRetries) {
 *             return FailureHandleResult.retry(Duration.ofSeconds(10));
 *         }
 *         return FailureHandleResult.giveUp();
 *     }
 * }
 * }</pre>
 *
 * @author T-018
 * @since 1.0.0
 */
public interface FailureHandler {

    /**
     * 处理续期失败
     *
     * @param taskId 任务 ID
     * @param keys 失败的 Key 集合
     * @param error 异常信息
     * @return 处理结果
     */
    FailureHandleResult handle(String taskId, Collection<String> keys, Throwable error);

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    String getName();

    /**
     * 失败处理结果
     */
    class FailureHandleResult {
        private final Action action;
        private final Duration retryDelay;

        private FailureHandleResult(Action action, Duration retryDelay) {
            this.action = action;
            this.retryDelay = retryDelay;
        }

        public static FailureHandleResult continueRenewal() {
            return new FailureHandleResult(Action.CONTINUE, null);
        }

        public static FailureHandleResult retry(Duration delay) {
            return new FailureHandleResult(Action.RETRY, delay);
        }

        public static FailureHandleResult giveUp() {
            return new FailureHandleResult(Action.GIVE_UP, null);
        }

        public Action getAction() { return action; }
        public Duration getRetryDelay() { return retryDelay; }

        public enum Action {
            CONTINUE,  // 继续下次续期
            RETRY,     // 延迟重试
            GIVE_UP    // 放弃任务
        }
    }
}

