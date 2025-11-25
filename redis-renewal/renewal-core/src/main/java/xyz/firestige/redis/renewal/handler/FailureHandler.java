package xyz.firestige.redis.renewal.handler;

import xyz.firestige.redis.renewal.Named;
import xyz.firestige.redis.renewal.RenewalContext;

/**
 * 失败处理器
 */
@FunctionalInterface
public interface FailureHandler extends Named {

    void handle(String key, Throwable error, RenewalContext context);
}
