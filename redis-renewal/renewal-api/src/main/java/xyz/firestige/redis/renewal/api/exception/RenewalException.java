package xyz.firestige.redis.renewal.api.exception;

public class RenewalException extends RuntimeException {
    public RenewalException(String message) { super(message); }
    public RenewalException(String message, Throwable cause) { super(message, cause); }
}
