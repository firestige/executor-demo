package xyz.firestige.deploy.util;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 测试时间监控扩展
 * 自动记录每个测试的执行时间，警告慢测试
 */
public class TimingExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final Logger logger = LoggerFactory.getLogger(TimingExtension.class);
    private static final String START_TIME = "startTime";

    /**
     * 慢测试阈值：60 秒
     */
    private static final long SLOW_TEST_THRESHOLD_MS = 60_000;

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        getStore(context).put(START_TIME, System.currentTimeMillis());
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        long startTime = getStore(context).remove(START_TIME, long.class);
        long duration = System.currentTimeMillis() - startTime;

        String testName = context.getDisplayName();

        // 警告慢测试
        if (duration > SLOW_TEST_THRESHOLD_MS) {
            logger.warn("⚠️  慢测试: {} 耗时 {} 秒", testName, duration / 1000.0);
        } else if (duration > 10_000) {
            // 超过 10 秒也记录一下
            logger.info("⏱️  测试: {} 耗时 {} 秒", testName, duration / 1000.0);
        }
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(Namespace.create(getClass(), context.getRequiredTestMethod()));
    }
}

