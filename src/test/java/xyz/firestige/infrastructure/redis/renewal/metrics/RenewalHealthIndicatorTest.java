package xyz.firestige.infrastructure.redis.renewal.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RenewalHealthIndicatorTest {

    @Test
    void health_normalOperation_returnsUp() {
        RenewalMetricsCollector collector = new RenewalMetricsCollector();
        collector.recordRenewal(100, 0);
        collector.updateActiveTaskCount(2);

        RenewalHealthIndicator indicator = new RenewalHealthIndicator(collector);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(2, health.getDetails().get("activeTaskCount"));
    }

    @Test
    void health_lowSuccessRate_returnsUnknown() {
        RenewalMetricsCollector collector = new RenewalMetricsCollector();
        collector.recordRenewal(60, 40); // 60% 成功率

        RenewalHealthIndicator indicator = new RenewalHealthIndicator(collector);
        Health health = indicator.health();

        assertEquals(Status.UNKNOWN, health.getStatus());
        assertTrue(health.getDetails().get("reason").toString().contains("成功率过低"));
    }

    @Test
    void health_tooManyTaskFailures_returnsUnknown() {
        RenewalMetricsCollector collector = new RenewalMetricsCollector();
        collector.recordRenewal(100, 0);

        for (int i = 0; i < 15; i++) {
            collector.recordTaskFailure();
        }

        RenewalHealthIndicator indicator = new RenewalHealthIndicator(collector);
        Health health = indicator.health();

        assertEquals(Status.UNKNOWN, health.getStatus());
        assertTrue(health.getDetails().get("reason").toString().contains("任务失败次数过多"));
    }
}

