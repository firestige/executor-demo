package xyz.firestige.redis.ack.core.exception;

/**
 * ACK 执行期内部异常
 * <p>
 * 仅供核心实现内部使用，不属于公共 API 契约。
 * 用于封装执行过程中的技术异常（Redis 写入失败、序列化失败、中断等）。
 *
 * @author AI
 * @since 1.0
 */
public class AckExecutionException extends RuntimeException {

    public AckExecutionException(String message) {
        super(message);
    }

    public AckExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

