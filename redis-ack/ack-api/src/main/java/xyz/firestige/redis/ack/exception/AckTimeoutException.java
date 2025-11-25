package xyz.firestige.redis.ack.exception;

/**
 * ACK 超时异常
 *
 * @author AI
 * @since 1.0
 */
public class AckTimeoutException extends AckException {

    public AckTimeoutException(String message) {
        super(message);
    }
}

