package xyz.firestige.deploy.config;

import xyz.firestige.deploy.config.stage.StageConfigurable;
import xyz.firestige.deploy.config.stage.ValidationResult;

/**
 * 蓝绿网关阶段配置 (Phase2 skeleton)
 */
public class BlueGreenGatewayStageConfig implements StageConfigurable {
    private Boolean enabled = true; // default enabled

    public static BlueGreenGatewayStageConfig defaultConfig() {
        return new BlueGreenGatewayStageConfig();
    }

    @Override
    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    // Future: add healthCheckPath, interval, steps etc.

    // Getters / Setters
    public Boolean getEnabled() {
        return enabled;
    }
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public ValidationResult validate() { // allow default success
        return StageConfigurable.super.validate();
    }
}

