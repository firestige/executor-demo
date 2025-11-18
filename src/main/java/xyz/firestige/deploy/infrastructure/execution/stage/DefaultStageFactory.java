package xyz.firestige.deploy.infrastructure.execution.stage;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.BroadcastStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.ConfigUpdateStep;

import java.util.List;

public class DefaultStageFactory implements StageFactory {
    @Override
    public List<TaskStage> buildStages(TenantConfig cfg) {
        // todo: 根据不同服务的配置生成对应的 Stage 列表，
        //  这里简化为固定的三个步骤，实际上ASBC的步骤不一样

        // 从 TenantConfig 中提取所需信息
        Long deployUnitVersion = cfg.getDeployUnit() != null ? cfg.getDeployUnit().version() : null;

        TaskStage stage = new CompositeServiceStage(
                "switch-service",
                List.of(
                        new ConfigUpdateStep("config-update", deployUnitVersion),
                        new BroadcastStep("broadcast-change")
                )
        );
        return List.of(stage);
    }
}

