package xyz.firestige.executor.unit.stage;

import org.junit.jupiter.api.Test;
import xyz.firestige.entity.deploy.NetworkEndpoint;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.domain.stage.steps.HealthCheckStep;
import xyz.firestige.executor.domain.task.TaskContext;
import xyz.firestige.executor.service.health.HealthCheckClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckStepFailureTest {

    static class StubClient implements HealthCheckClient {
        private final List<Boolean> sequence;
        private int idx;
        StubClient(List<Boolean> seq) { this.sequence = seq; }
        @Override
        public Map<String, Object> get(String url) {
            boolean ok = idx < sequence.size() && sequence.get(idx++);
            return Map.of("version", ok ? "1" : "0");
        }
    }

    private NetworkEndpoint ep() { NetworkEndpoint e = new NetworkEndpoint(); e.setTargetDomain("localhost"); return e; }

    @Test
    void testAllFail() {
        ExecutorProperties props = new ExecutorProperties();
        props.setHealthCheckIntervalSeconds(0); // speed up
        props.setHealthCheckMaxAttempts(3);
        HealthCheckStep step = new HealthCheckStep("hc", List.of(ep()), "1", "version", new StubClient(List.of(false, false, false)), props);
        assertThrows(IllegalStateException.class, () -> step.execute(new TaskContext("t1")));
    }

    @Test
    void testLastAttemptSuccess() throws Exception {
        ExecutorProperties props = new ExecutorProperties();
        props.setHealthCheckIntervalSeconds(0);
        props.setHealthCheckMaxAttempts(3);
        HealthCheckStep step = new HealthCheckStep("hc", List.of(ep()), "1", "version", new StubClient(List.of(false, false, true)), props);
        step.execute(new TaskContext("t2")); // should not throw
    }

    @Test
    void testPartialEndpointsFail() {
        ExecutorProperties props = new ExecutorProperties();
        props.setHealthCheckIntervalSeconds(0);
        props.setHealthCheckMaxAttempts(2);
        List<NetworkEndpoint> eps = new ArrayList<>();
        eps.add(ep());
        NetworkEndpoint ep2 = new NetworkEndpoint(); ep2.setTargetDomain("127.0.0.1"); eps.add(ep2);
        // first attempt: one ok one fail; second attempt both fail => fail
        HealthCheckStep step = new HealthCheckStep("hc", eps, "1", "version", new StubClient(List.of(true, false, false, false)), props);
        assertThrows(IllegalStateException.class, () -> step.execute(new TaskContext("t3")));
    }
}

