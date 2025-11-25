/**
 * Redis ACK 服务核心 API
 * <p>
 * 提供 Write → Publish → Verify 流式配置确认链路的接口定义。
 * 此包不包含任何实现，仅定义契约，使用者可基于接口实现自己的版本。
 * <p>
 * 核心接口：
 * <ul>
 *   <li>{@link xyz.firestige.redis.ack.api.RedisAckService} - 服务入口</li>
 *   <li>{@link xyz.firestige.redis.ack.api.WriteStageBuilder} - Write 阶段构建器</li>
 *   <li>{@link xyz.firestige.redis.ack.api.PubSubStageBuilder} - Pub/Sub 阶段构建器</li>
 *   <li>{@link xyz.firestige.redis.ack.api.VerifyStageBuilder} - Verify 阶段构建器</li>
 *   <li>{@link xyz.firestige.redis.ack.api.FootprintExtractor} - Footprint 提取策略接口</li>
 *   <li>{@link xyz.firestige.redis.ack.api.AckEndpoint} - 验证端点接口</li>
 *   <li>{@link xyz.firestige.redis.ack.api.RetryStrategy} - 重试策略接口</li>
 *   <li>{@link xyz.firestige.redis.ack.api.AckMetricsRecorder} - 指标记录器接口</li>
 * </ul>
 * <p>
 * 数据模型：
 * <ul>
 *   <li>{@link xyz.firestige.redis.ack.api.AckResult} - 执行结果</li>
 *   <li>{@link xyz.firestige.redis.ack.api.AckContext} - 执行上下文</li>
 *   <li>{@link xyz.firestige.redis.ack.api.RedisOperation} - Redis 操作类型枚举</li>
 * </ul>
 *
 * @author AI
 * @since 1.0
 */
package xyz.firestige.redis.ack.api;

