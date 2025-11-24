package xyz.firestige.infrastructure.redis.renewal.metrics;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RenewalMetricsCollectorTest {

    @Test
    void recordRenewal_updatesCounters() {
        RenewalMetricsCollector collector = new RenewalMetricsCollector();

        collector.recordRenewal(10, 2);

        assertEquals(1, collector.getTotalRenewals());
        assertEquals(10, collector.getSuccessCount());
        assertEquals(2, collector.getFailureCount());
        assertNotNull(collector.getLastRenewalTime());
    }

    @Test
    void getSuccessRate_calculatesCorrectly() {
        RenewalMetricsCollector collector = new RenewalMetricsCollector();

        collector.recordRenewal(80, 20);

        assertEquals(80.0, collector.getSuccessRate(), 0.01);
    }

    @Test
    void getSuccessRate_noData_returns100() {
        RenewalMetricsCollector collector = new RenewalMetricsCollector();
        assertEquals(100.0, collector.getSuccessRate());
    }

    @Test
    void recordTaskFailure_incrementsCounter() {
        RenewalMetricsCollector collector = new RenewalMetricsCollector();

        collector.recordTaskFailure();
        collector.recordTaskFailure();

        assertEquals(2, collector.getTaskFailures());
    }

    @Test
    void updateActiveTaskCount_setsValue() {
        RenewalMetricsCollector collector = new RenewalMetricsCollector();

        collector.updateActiveTaskCount(5);

        assertEquals(5, collector.getActiveTaskCount());
    }

    @Test
    void snapshot_capturesCurrentState() {
        RenewalMetricsCollector collector = new RenewalMetricsCollector();

        collector.recordRenewal(10, 2);
        collector.updateActiveTaskCount(3);

        RenewalMetricsCollector.MetricsSnapshot snapshot = collector.snapshot();

        assertEquals(1, snapshot.getTotalRenewals());
        assertEquals(10, snapshot.getSuccessCount());
        assertEquals(2, snapshot.getFailureCount());
        assertEquals(3, snapshot.getActiveTaskCount());
        assertNotNull(snapshot.getLastRenewalTime());
    }

    @Test
    void reset_clearsAllMetrics() {
        RenewalMetricsCollector collector = new RenewalMetricsCollector();

        collector.recordRenewal(10, 2);
        collector.recordTaskFailure();
        collector.reset();

        assertEquals(0, collector.getTotalRenewals());
        assertEquals(0, collector.getSuccessCount());
        assertEquals(0, collector.getFailureCount());
        assertEquals(0, collector.getTaskFailures());
        assertNull(collector.getLastRenewalTime());
    }
}

