/**
 * Redis Renewal 服务核心 API
 * <p>
 * 提供 Redis Key TTL 自动续期的接口定义。
 * 核心接口：
 * <ul>
 *   <li>{@link xyz.firestige.redis.renewal.api.RenewalService} - 服务入口</li>
 *   <li>{@link xyz.firestige.redis.renewal.api.TtlStrategy} - TTL 策略</li>
 *   <li>{@link xyz.firestige.redis.renewal.api.IntervalStrategy} - 间隔策略</li>
 *   <li>{@link xyz.firestige.redis.renewal.api.StopCondition} - 停止条件</li>
 * </ul>
 */
package xyz.firestige.redis.renewal.api;
