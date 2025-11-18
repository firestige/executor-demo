package xyz.firestige.deploy.unit.execution;

import org.junit.jupiter.api.Test;
import xyz.firestige.deploy.event.SpringTaskEventSink;
import xyz.firestige.deploy.metrics.MetricsRegistry;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.execution.HeartbeatScheduler;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HeartbeatGaugeTest {
    static class SpyMetrics implements MetricsRegistry {
        volatile String lastName; volatile double lastValue; volatile int count;
        public void incrementCounter(String name) { /* ignore */ }
        public void setGauge(String name, double value) { lastName=name; lastValue=value; count++; }
    }

    @Test
    void gaugeReportedOnStartAndUpdates() throws Exception {
        SpyMetrics m = new SpyMetrics();
        TaskStateManager sm = new TaskStateManager(e -> {});
        SpringTaskEventSink sink = new SpringTaskEventSink(sm);
        AtomicInteger completed = new AtomicInteger(0);
        HeartbeatScheduler hb = new HeartbeatScheduler("p","t",3, completed::get, sink, 1, m, "heartbeat_lag");
        hb.start();
        Thread.sleep(150); // allow first run
        assertEquals("heartbeat_lag", m.lastName);
        assertTrue(m.lastValue >= 0);
        completed.set(2);
        Thread.sleep(1100);
        assertTrue(m.lastValue <= 1.0, "lag should reduce as completed increases");
        hb.stop();
    }
}

