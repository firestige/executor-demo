package xyz.firestige.deploy.config;

import xyz.firestige.deploy.config.stage.StageConfigurable;
import xyz.firestige.deploy.config.stage.ValidationResult;

/**
 * ASBC 网关阶段配置 (Phase2 skeleton)
 */
public class ASBCGatewayStageConfig implements StageConfigurable {
    private Boolean enabled = false; // default disabled

    public static ASBCGatewayStageConfig defaultConfig() {
        return new ASBCGatewayStageConfig();
    }

    @Override
    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    @Override
    public ValidationResult validate() {
        return StageConfigurable.super.validate();
    }
}

