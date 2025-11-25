/**
 * Redis ACK 服务核心实现
 * <p>
 * 提供 API 的默认实现，不依赖 Spring 框架（仅依赖 Spring Data Redis、Jackson 作为技术选型）。
 * HTTP 客户端通过 {@link xyz.firestige.redis.ack.api.HttpClient} 接口抽象，具体实现由上层模块提供。
 * <p>
 * 核心类：
 * <ul>
 *   <li>{@link xyz.firestige.redis.ack.core.DefaultRedisAckService} - 默认服务实现</li>
 *   <li>{@link xyz.firestige.redis.ack.core.AckExecutor} - 执行协调器</li>
 *   <li>{@link xyz.firestige.redis.ack.core.AckTask} - 任务封装</li>
 *   <li>{@link xyz.firestige.redis.ack.core.WriteStageBuilderImpl} - Write Builder 实现</li>
 *   <li>{@link xyz.firestige.redis.ack.core.PubSubStageBuilderImpl} - Pub/Sub Builder 实现</li>
 *   <li>{@link xyz.firestige.redis.ack.core.VerifyStageBuilderImpl} - Verify Builder 实现</li>
 * </ul>
 * <p>
 * 扩展包：
 * <ul>
 *   <li>{@link xyz.firestige.redis.ack.endpoint} - 端点实现（HTTP GET/POST）</li>
 *   <li>{@link xyz.firestige.redis.ack.extractor} - Footprint 提取器（JSON/Regex/Function）</li>
 *   <li>{@link xyz.firestige.redis.ack.retry} - 重试策略（Fixed/Exponential/Custom）</li>
 * </ul>
 *
 * @author AI
 * @since 1.0
 */
package xyz.firestige.redis.ack.core;

