/**
 * Micrometer 指标集成
 * <p>
 * 提供基于 Micrometer 的 ACK 指标记录实现。
 * <p>
 * 记录的指标：
 * <ul>
 *   <li>redis_ack_executions - 总执行次数</li>
 *   <li>redis_ack_success - 成功次数</li>
 *   <li>redis_ack_mismatch - Footprint 不匹配次数</li>
 *   <li>redis_ack_timeout - 超时次数</li>
 *   <li>redis_ack_error - 错误次数</li>
 *   <li>redis_ack_duration - 执行耗时分布</li>
 * </ul>
 *
 * @author AI
 * @since 1.0
 */
package xyz.firestige.redis.ack.spring.metrics;

