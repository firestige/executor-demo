package xyz.firestige.deploy.config;

import xyz.firestige.deploy.config.stage.StageConfigurable;
import xyz.firestige.deploy.config.stage.ValidationResult;

/**
 * Portal 阶段配置 (Phase2 skeleton)
 */
public class PortalStageConfig implements StageConfigurable {
    private Boolean enabled = true; // default enabled

    public static PortalStageConfig defaultConfig() {
        return new PortalStageConfig();
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

