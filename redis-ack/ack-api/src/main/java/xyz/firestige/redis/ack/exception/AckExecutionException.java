package xyz.firestige.redis.ack.exception;

/**
 * ACK 执行异常
 *
 * <p>当 ACK 流程执行过程中发生非超时错误时抛出
 *
 * @author AI
 * @since 1.0
 */
public class AckExecutionException extends AckException {

    public AckExecutionException(String message) {
        super(message);
    }

    public AckExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

