package xyz.firestige.executor.unit.application;

import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.domain.stage.CompositeServiceStage;
import xyz.firestige.executor.domain.stage.StageFactory;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.stage.steps.BroadcastStep;
import xyz.firestige.executor.domain.stage.steps.ConfigUpdateStep;
import xyz.firestige.executor.domain.stage.steps.HealthCheckStep;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.service.health.HealthCheckClient;

import java.util.List;

/**
 * Test multi-stage factory: produces two stages to allow rollback/retry tests.
 */
public class TestMultiStageFactory implements StageFactory {
    @Override
    public List<TaskStage> buildStages(TaskAggregate task, TenantDeployConfig cfg, ExecutorProperties props, HealthCheckClient healthClient) {
        Long baseVersion = cfg.getDeployUnitVersion() != null ? cfg.getDeployUnitVersion() : 100L;
        Long secondVersion = baseVersion + 1;
        TaskStage stage1 = new CompositeServiceStage(
                "stage-1-initial",
                List.of(
                        new ConfigUpdateStep("config-update-1", baseVersion),
                        new BroadcastStep("broadcast-1"),
                        new HealthCheckStep("health-1", cfg.getNetworkEndpoints() != null ? cfg.getNetworkEndpoints() : List.of(), String.valueOf(baseVersion), props.getHealthCheckVersionKey(), healthClient, props)
                )
        );
        TaskStage stage2 = new CompositeServiceStage(
                "stage-2-upgrade",
                List.of(
                        new ConfigUpdateStep("config-update-2", secondVersion),
                        new BroadcastStep("broadcast-2"),
                        new HealthCheckStep("health-2", cfg.getNetworkEndpoints() != null ? cfg.getNetworkEndpoints() : List.of(), String.valueOf(secondVersion), props.getHealthCheckVersionKey(), healthClient, props)
                )
        );
        return List.of(stage1, stage2);
    }
}
