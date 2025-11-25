package xyz.firestige.redis.renewal.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.infrastructure.redis.renewal.api.FailureHandler;

import java.util.Collection;

/**
 * 日志记录失败处理器（默认实现）
 * <p>记录错误日志后继续续期
 */
public class LogAndContinueFailureHandler implements FailureHandler {
    private static final Logger log = LoggerFactory.getLogger(LogAndContinueFailureHandler.class);

    @Override
    public FailureHandleResult handle(String taskId, Collection<String> keys, Throwable error) {
        log.error("续期失败 [taskId={}, keys={}, error={}]", taskId, keys.size(), error.getMessage(), error);
        return FailureHandleResult.continueRenewal();
    }

    @Override
    public String getName() {
        return "LogAndContinue";
    }
}

