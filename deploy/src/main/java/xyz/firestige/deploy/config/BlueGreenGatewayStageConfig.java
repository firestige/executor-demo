package xyz.firestige.deploy.config;

import xyz.firestige.deploy.config.stage.StageConfigurable;
import xyz.firestige.deploy.config.stage.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝绿网关阶段配置 (Phase3 enriched)
 */
public class BlueGreenGatewayStageConfig implements StageConfigurable {
    private Boolean enabled = true; // default enabled
    private String healthCheckPath = "/health";
    private String healthCheckVersionKey = "version";
    private Integer healthCheckIntervalSeconds = 3;
    private Integer healthCheckMaxAttempts = 10;
    private List<StepConfig> steps = new ArrayList<>();

    public static BlueGreenGatewayStageConfig defaultConfig() {
        BlueGreenGatewayStageConfig cfg = new BlueGreenGatewayStageConfig();
        cfg.setSteps(defaultSteps());
        return cfg;
    }

    private static List<StepConfig> defaultSteps(){
        List<StepConfig> list = new ArrayList<>();
        list.add(StepConfig.redisWrite("gateway:config:{tenantId}"));
        list.add(StepConfig.healthCheck());
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
        if(healthCheckPath == null || healthCheckPath.isBlank()){
            result.warning("healthCheckPath 空, 使用默认 /health");
            healthCheckPath = "/health";
        }
        if(healthCheckIntervalSeconds == null || healthCheckIntervalSeconds <=0){
            result.warning("healthCheckIntervalSeconds 无效, 使用默认 3");
            healthCheckIntervalSeconds = 3;
        }
        if(healthCheckMaxAttempts == null || healthCheckMaxAttempts <=0){
            result.warning("healthCheckMaxAttempts 无效, 使用默认 10");
            healthCheckMaxAttempts = 10;
        }
        if(steps == null || steps.isEmpty()){
            result.warning("未配置 steps, 使用默认步骤");
            steps = defaultSteps();
        }
        return result.build();
    }

    // Getters / Setters
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getHealthCheckPath() { return healthCheckPath; }
    public void setHealthCheckPath(String healthCheckPath) { this.healthCheckPath = healthCheckPath; }
    public String getHealthCheckVersionKey() { return healthCheckVersionKey; }
    public void setHealthCheckVersionKey(String healthCheckVersionKey) { this.healthCheckVersionKey = healthCheckVersionKey; }
    public Integer getHealthCheckIntervalSeconds() { return healthCheckIntervalSeconds; }
    public void setHealthCheckIntervalSeconds(Integer healthCheckIntervalSeconds) { this.healthCheckIntervalSeconds = healthCheckIntervalSeconds; }
    public Integer getHealthCheckMaxAttempts() { return healthCheckMaxAttempts; }
    public void setHealthCheckMaxAttempts(Integer healthCheckMaxAttempts) { this.healthCheckMaxAttempts = healthCheckMaxAttempts; }
    public List<StepConfig> getSteps() { return steps; }
    public void setSteps(List<StepConfig> steps) { this.steps = steps; }
}
