package xyz.firestige.executor.unit.stage;

import org.junit.jupiter.api.Test;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.entity.deploy.NetworkEndpoint;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.domain.stage.DefaultStageFactory;
import xyz.firestige.executor.domain.stage.StageFactory;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.stage.StageStep;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.service.health.HealthCheckClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultStageFactoryTest {

    static class CapturingClient implements HealthCheckClient {
        volatile String lastUrl;
        private final Map<String,Object> resp;
        CapturingClient(Map<String,Object> resp){ this.resp = resp; }
        @Override public Map<String, Object> get(String url) { lastUrl = url; return resp; }
    }

    @Test
    void buildsFifoStagesAndRespectsProps() throws Exception {
        // given props with custom path/versionKey
        ExecutorProperties props = new ExecutorProperties();
        props.setHealthCheckPath("/hc");
        props.setHealthCheckVersionKey("ver");
        // tenant config
        TenantDeployConfig cfg = new TenantDeployConfig();
        cfg.setDeployUnitVersion(2L);
        NetworkEndpoint ep = new NetworkEndpoint(); ep.setTargetDomain("service-A");
        cfg.setNetworkEndpoints(List.of(ep));
        TaskAggregate task = new TaskAggregate("t-factory", "p", "ten");
        // client returns expected version under custom key
        CapturingClient client = new CapturingClient(Map.of("ver","2"));
        StageFactory factory = new DefaultStageFactory();
        List<TaskStage> stages = factory.buildStages(task, cfg, props, client);
        assertEquals(1, stages.size());
        TaskStage stage = stages.get(0);
        assertEquals("switch-service", stage.getName());
        List<StageStep> steps = stage.getSteps();
        assertEquals(3, steps.size());
        assertEquals("config-update", steps.get(0).getStepName());
        assertEquals("broadcast-change", steps.get(1).getStepName());
        assertEquals("health-check", steps.get(2).getStepName());
        // execute last step to verify path/versionKey used
        steps.get(2).execute(new TaskRuntimeContext("p","t-factory","ten", null));
        assertNotNull(client.lastUrl);
        assertTrue(client.lastUrl.startsWith("http://service-A"));
        assertTrue(client.lastUrl.endsWith("/hc"));
    }
}

