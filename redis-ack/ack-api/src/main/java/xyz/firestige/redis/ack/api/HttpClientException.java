package xyz.firestige.redis.ack.api;

/**
 * HTTP 客户端异常
 *
 * @author AI
 * @since 1.0
 */
public class HttpClientException extends RuntimeException {

    public HttpClientException(String message) {
        super(message);
    }

    public HttpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}

