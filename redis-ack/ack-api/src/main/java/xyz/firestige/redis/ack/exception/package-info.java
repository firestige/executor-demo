/**
 * Redis ACK 异常类型
 * <p>
 * 定义跨层语义异常，供 API 使用者和实现者共同使用。
 * <ul>
 *   <li>{@link xyz.firestige.redis.ack.exception.AckException} - 基础异常</li>
 *   <li>{@link xyz.firestige.redis.ack.exception.AckTimeoutException} - 超时异常</li>
 *   <li>{@link xyz.firestige.redis.ack.exception.AckEndpointException} - 端点异常</li>
 *   <li>{@link xyz.firestige.redis.ack.exception.FootprintExtractionException} - Footprint 提取异常</li>
 * </ul>
 * <p>
 * 注意：AckExecutionException 已移至 core 层，作为内部实现异常使用。
 *
 * @author AI
 * @since 1.0
 */
package xyz.firestige.redis.ack.exception;

