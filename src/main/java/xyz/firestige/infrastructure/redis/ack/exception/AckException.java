package xyz.firestige.infrastructure.redis.ack.exception;

/**
 * ACK 异常基类
 *
 * @author AI
 * @since 1.0
 */
public class AckException extends RuntimeException {

    public AckException(String message) {
        super(message);
    }

    public AckException(String message, Throwable cause) {
        super(message, cause);
    }
}

