package xyz.firestige.redis.renewal.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 续期指标定时报告器
 * <p>定期打印续期服务的运行指标
 */
public class RenewalMetricsReporter {
    private static final Logger log = LoggerFactory.getLogger(RenewalMetricsReporter.class);

    private final RenewalMetricsCollector collector;
    private final ScheduledExecutorService scheduler;
    private final long intervalSeconds;

    public RenewalMetricsReporter(RenewalMetricsCollector collector, long intervalSeconds) {
        this.collector = collector;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "renewal-metrics-reporter");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动定时报告
     */
    public void start() {
        scheduler.scheduleAtFixedRate(
            this::report,
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        );
        log.info("续期指标报告器已启动，间隔: {}秒", intervalSeconds);
    }

    /**
     * 停止报告
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("续期指标报告器已停止");
    }

    /**
     * 打印指标报告
     */
    private void report() {
        RenewalMetricsCollector.MetricsSnapshot snapshot = collector.snapshot();

        StringBuilder report = new StringBuilder("\n");
        report.append("==================== Redis 续期服务指标 ====================\n");
        report.append(String.format("活跃任务数:      %d\n", snapshot.getActiveTaskCount()));
        report.append(String.format("总续期次数:      %d\n", snapshot.getTotalRenewals()));
        report.append(String.format("成功 Key 数:     %d\n", snapshot.getSuccessCount()));
        report.append(String.format("失败 Key 数:     %d\n", snapshot.getFailureCount()));
        report.append(String.format("成功率:          %.2f%%\n", snapshot.getSuccessRate()));
        report.append(String.format("任务失败次数:    %d\n", snapshot.getTaskFailures()));

        Instant lastTime = snapshot.getLastRenewalTime();
        if (lastTime != null) {
            report.append(String.format("最后续期时间:    %s\n", lastTime));
        } else {
            report.append("最后续期时间:    N/A\n");
        }
        report.append("===========================================================");

        log.info(report.toString());
    }
}

