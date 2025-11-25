/**
 * 重试策略实现
 * <p>
 * 提供多种预置的重试策略：
 * <ul>
 *   <li>{@link xyz.firestige.redis.ack.retry.FixedDelayRetryStrategy} - 固定延迟重试</li>
 *   <li>{@link xyz.firestige.redis.ack.retry.ExponentialBackoffRetryStrategy} - 指数退避重试</li>
 * </ul>
 * <p>
 * 使用者可实现 {@link xyz.firestige.redis.ack.api.RetryStrategy} 接口以定义自定义重试逻辑。
 *
 * @author AI
 * @since 1.0
 */
package xyz.firestige.redis.ack.retry;

