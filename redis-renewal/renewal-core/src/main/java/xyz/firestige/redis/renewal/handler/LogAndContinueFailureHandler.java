package xyz.firestige.redis.renewal.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.redis.renewal.RenewalContext;

/**
 * 日志记录失败处理器（默认实现）
 * <p>记录错误日志后继续续期
 */
public class LogAndContinueFailureHandler implements FailureHandler {
    private static final Logger log = LoggerFactory.getLogger(LogAndContinueFailureHandler.class);

    @Override
    public String getName() {
        return "LogAndContinue";
    }

    @Override
    public void handle(String key, Throwable error, RenewalContext context) {
        log.error("续期失败 [taskId={}, key={}, error={}]", context.getTaskId(), key, error.getMessage(), error);
    }
}

