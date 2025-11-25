/**
 * ACK 端点实现
 * <p>
 * 提供 HTTP 端点的默认实现：
 * <ul>
 *   <li>{@link xyz.firestige.redis.ack.endpoint.HttpGetEndpoint} - HTTP GET 请求</li>
 *   <li>{@link xyz.firestige.redis.ack.endpoint.HttpPostEndpoint} - HTTP POST 请求</li>
 * </ul>
 * <p>
 * 使用者可实现 {@link xyz.firestige.redis.ack.api.AckEndpoint} 接口以支持其他协议。
 *
 * @author AI
 * @since 1.0
 */
package xyz.firestige.redis.ack.endpoint;

