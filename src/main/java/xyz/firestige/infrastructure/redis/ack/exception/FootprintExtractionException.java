package xyz.firestige.infrastructure.redis.ack.exception;

/**
 * Footprint 提取异常
 *
 * @author AI
 * @since 1.0
 */
public class FootprintExtractionException extends AckException {

    public FootprintExtractionException(String message) {
        super(message);
    }

    public FootprintExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}

