/**
 * Spring Boot 自动配置
 * <p>
 * 为 Redis ACK 服务提供 Spring Boot 自动装配支持。
 * <p>
 * 核心组件：
 * <ul>
 *   <li>{@link xyz.firestige.redis.ack.spring.autoconfigure.RedisAckAutoConfiguration} - 自动配置类</li>
 *   <li>{@link xyz.firestige.redis.ack.spring.autoconfigure.RedisAckProperties} - 配置属性</li>
 *   <li>{@link xyz.firestige.redis.ack.spring.autoconfigure.RedisAckHealthIndicator} - 健康检查指示器</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>
 * # application.yml
 * redis:
 *   ack:
 *     enabled: true
 *     default-timeout: 60s
 *     http:
 *       connect-timeout: 5s
 *       read-timeout: 10s
 * </pre>
 *
 * @author AI
 * @since 1.0
 */
package xyz.firestige.redis.ack.spring.autoconfigure;

