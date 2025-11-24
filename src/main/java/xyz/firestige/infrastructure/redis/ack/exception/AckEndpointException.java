package xyz.firestige.infrastructure.redis.ack.exception;

/**
 * ACK 端点查询异常
 *
 * @author AI
 * @since 1.0
 */
public class AckEndpointException extends AckException {

    public AckEndpointException(String message) {
        super(message);
    }

    public AckEndpointException(String message, Throwable cause) {
        super(message, cause);
    }
}

