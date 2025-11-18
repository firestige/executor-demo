package xyz.firestige.deploy.unit.stage;

import org.junit.jupiter.api.Test;
import xyz.firestige.entity.deploy.NetworkEndpoint;
import xyz.firestige.deploy.config.ExecutorProperties;
import xyz.firestige.deploy.domain.stage.steps.HealthCheckStep;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.service.health.HealthCheckClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class HealthCheckStepFailureTest {

    static class StubClient implements HealthCheckClient {
        private final Map<String,Object> map;
        StubClient(Map<String,Object> map){ this.map = map; }
        @Override public Map<String, Object> get(String url) { return map; }
    }

    @Test
    void failsAfterMaxAttemptsWhenNotHealthy() {
        ExecutorProperties props = new ExecutorProperties();
        props.setHealthCheckIntervalSeconds(0); // speed up
        props.setHealthCheckMaxAttempts(3);
        var step = new HealthCheckStep("hc", List.of(new NetworkEndpoint()), "1", "version", new StubClient(Map.of("version","0")), props);
        assertThrows(IllegalStateException.class, () -> step.execute(new TaskRuntimeContext("p","t1","ten", null)));
    }

    @Test
    void succeedsWhenBecomesHealthy() throws Exception {
        ExecutorProperties props = new ExecutorProperties();
        props.setHealthCheckIntervalSeconds(0);
        props.setHealthCheckMaxAttempts(3);
        var step = new HealthCheckStep("hc", List.of(new NetworkEndpoint()), "1", "version", new StubClient(Map.of("version","1")), props);
        step.execute(new TaskRuntimeContext("p","t2","ten", null));
    }

    @Test
    void failsWhenVersionKeyMissing() {
        ExecutorProperties props = new ExecutorProperties();
        props.setHealthCheckIntervalSeconds(0);
        props.setHealthCheckMaxAttempts(2);
        var step = new HealthCheckStep("hc", List.of(new NetworkEndpoint()), "1", "version", new StubClient(Map.of()), props);
        assertThrows(IllegalStateException.class, () -> step.execute(new TaskRuntimeContext("p","t3","ten", null)));
    }
}
