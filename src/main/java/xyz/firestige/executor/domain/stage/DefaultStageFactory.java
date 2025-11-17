package xyz.firestige.executor.domain.stage;

import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.domain.stage.steps.BroadcastStep;
import xyz.firestige.executor.domain.stage.steps.ConfigUpdateStep;
import xyz.firestige.executor.domain.stage.steps.HealthCheckStep;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.service.health.HealthCheckClient;

import java.util.List;

public class DefaultStageFactory implements StageFactory {
    @Override
    public List<TaskStage> buildStages(TaskAggregate task,
                                       TenantDeployConfig cfg,
                                       ExecutorProperties props,
                                       HealthCheckClient healthClient) {
        // todo: 根据不同服务的配置生成对应的 Stage 列表，
        //  这里简化为固定的三个步骤，实际上ASBC的步骤不一样
        TaskStage stage = new CompositeServiceStage(
                "switch-service",
                List.of(
                        new ConfigUpdateStep("config-update", cfg.getDeployUnitVersion()),
                        new BroadcastStep("broadcast-change"),
                        new HealthCheckStep(
                                "health-check",
                                cfg.getNetworkEndpoints() != null ? cfg.getNetworkEndpoints() : List.of(),
                                String.valueOf(cfg.getDeployUnitVersion()),
                                props.getHealthCheckVersionKey(),
                                healthClient,
                                props
                        )
                )
        );
        return List.of(stage);
    }
}

