package xyz.firestige.redis.ack.exception;

/**
 * VersionTag 提取异常
 * <p>
 * 当从值对象中提取 versionTag 失败时抛出
 *
 * @author AI
 * @since 2.0
 */
public class VersionTagExtractionException extends RuntimeException {

    public VersionTagExtractionException(String message) {
        super(message);
    }

    public VersionTagExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}

