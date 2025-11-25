/**
 * 核心实现层内部异常
 * <p>
 * 仅供 ack-core 模块内部使用，不对外暴露。
 * 封装执行过程中的技术异常，如 Redis 操作失败、序列化失败、线程中断等。
 * <p>
 * 注意：此包与 ack-api 的 {@code xyz.firestige.redis.ack.exception} 完全独立，
 * 避免 JPMS 分包（split package）问题。
 *
 * @author AI
 * @since 1.0
 */
package xyz.firestige.redis.ack.core.exception;

