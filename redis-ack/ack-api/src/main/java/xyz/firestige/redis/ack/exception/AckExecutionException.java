package xyz.firestige.redis.ack.exception;

/**
 * ACK 执行异常
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

