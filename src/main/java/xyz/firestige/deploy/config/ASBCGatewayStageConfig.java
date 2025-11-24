package xyz.firestige.deploy.config;

import xyz.firestige.deploy.config.stage.StageConfigurable;
import xyz.firestige.deploy.config.stage.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * ASBC 网关阶段配置 (Phase3 enriched)
 */
public class ASBCGatewayStageConfig implements StageConfigurable {
    private Boolean enabled = false; // default disabled
    private List<StepConfig> steps = new ArrayList<>();

    public static ASBCGatewayStageConfig defaultConfig() {
        ASBCGatewayStageConfig cfg = new ASBCGatewayStageConfig();
        cfg.setSteps(defaultSteps());
        return cfg;
    }

    private static List<StepConfig> defaultSteps(){
        List<StepConfig> list = new ArrayList<>();
        list.add(StepConfig.httpRequest("POST", "http://asbc-gateway/api/deploy"));
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
            result.warning("ASBC 未配置 steps, 使用默认步骤");
            steps = defaultSteps();
        }
        return result.build();
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public List<StepConfig> getSteps() { return steps; }
    public void setSteps(List<StepConfig> steps) { this.steps = steps; }
}
