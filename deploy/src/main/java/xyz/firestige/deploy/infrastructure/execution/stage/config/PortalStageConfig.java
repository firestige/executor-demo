package xyz.firestige.deploy.infrastructure.execution.stage.config;

import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.StageConfigurable;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Portal 阶段配置 (Phase3 enriched)
 */
public class PortalStageConfig implements StageConfigurable {
    private Boolean enabled = true; // default enabled
    private List<StepConfig> steps = new ArrayList<>();

    public static PortalStageConfig defaultConfig() {
        PortalStageConfig cfg = new PortalStageConfig();
        cfg.setSteps(defaultSteps());
        return cfg;
    }

    private static List<StepConfig> defaultSteps(){
        List<StepConfig> list = new ArrayList<>();
        list.add(StepConfig.redisWrite("portal:config:{tenantId}"));
        list.add(StepConfig.pubsubBroadcast("portal:reload"));
        return list;
    }

    @Override
    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    @Override
    public ValidationResult validate() {
        ValidationResult.Builder result = ValidationResult.builder();
        if(!isEnabled()) return result.build();
        if(steps == null || steps.isEmpty()){
            result.warning("Portal 未配置 steps, 使用默认步骤");
            steps = defaultSteps();
        }
        return result.build();
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public List<StepConfig> getSteps() { return steps; }
    public void setSteps(List<StepConfig> steps) { this.steps = steps; }
}
