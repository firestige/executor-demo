package xyz.firestige.redis.renewal.metrics;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Duration;
import java.time.Instant;

/**
 * 续期服务健康检查指示器
 * <p>集成 Spring Boot Actuator 健康检查
 */
public class RenewalHealthIndicator implements HealthIndicator {

    private final RenewalMetricsCollector collector;
    private final double warningSuccessRateThreshold;
    private final Duration staleThreshold;

    public RenewalHealthIndicator(RenewalMetricsCollector collector) {
        this(collector, 80.0, Duration.ofMinutes(5));
    }

    public RenewalHealthIndicator(RenewalMetricsCollector collector,
                                 double warningSuccessRateThreshold,
                                 Duration staleThreshold) {
        this.collector = collector;
        this.warningSuccessRateThreshold = warningSuccessRateThreshold;
        this.staleThreshold = staleThreshold;
    }

    @Override
    public Health health() {
        RenewalMetricsCollector.MetricsSnapshot snapshot = collector.snapshot();

        Health.Builder builder = new Health.Builder();

        // 添加详细信息
        builder.withDetail("activeTaskCount", snapshot.getActiveTaskCount())
               .withDetail("totalRenewals", snapshot.getTotalRenewals())
               .withDetail("successCount", snapshot.getSuccessCount())
               .withDetail("failureCount", snapshot.getFailureCount())
               .withDetail("successRate", String.format("%.2f%%", snapshot.getSuccessRate()))
               .withDetail("taskFailures", snapshot.getTaskFailures());

        Instant lastTime = snapshot.getLastRenewalTime();
        if (lastTime != null) {
            builder.withDetail("lastRenewalTime", lastTime.toString());

            // 检查是否过期（长时间未续期）
            Duration timeSinceLastRenewal = Duration.between(lastTime, Instant.now());
            if (snapshot.getActiveTaskCount() > 0 && timeSinceLastRenewal.compareTo(staleThreshold) > 0) {
                return builder.down()
                    .withDetail("reason", "长时间未续期: " + timeSinceLastRenewal.toMinutes() + " 分钟")
                    .build();
            }
        }

        // 检查成功率
        double successRate = snapshot.getSuccessRate();
        if (successRate < warningSuccessRateThreshold) {
            return builder.unknown()
                .withDetail("reason", "成功率过低: " + String.format("%.2f%%", successRate))
                .build();
        }

        // 检查任务失败
        if (snapshot.getTaskFailures() > 10) {
            return builder.unknown()
                .withDetail("reason", "任务失败次数过多: " + snapshot.getTaskFailures())
                .build();
        }

        return builder.up().build();
    }
}

