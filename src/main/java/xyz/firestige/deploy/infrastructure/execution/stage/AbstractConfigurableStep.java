package xyz.firestige.deploy.infrastructure.execution.stage;

import xyz.firestige.deploy.domain.stage.config.ServiceConfig;

import java.util.Map;
import java.util.Objects;

/**
 * 可配置步骤的抽象基类
 * 
 * 职责：
 * 1. 封装通用的配置注入逻辑
 * 2. 提供 ServiceConfig + StepConfig 的双重注入
 * 3. 定义模板方法供子类实现
 * 
 * 设计：
 * - stepConfig: 来自 YAML 配置（固定基础设施配置）
 * - serviceConfig: 来自防腐层转换（运行时业务数据）
 */
public abstract class AbstractConfigurableStep implements StageStep {
    
    protected final String stepName;
    protected final Map<String, Object> stepConfig;     // 来自 YAML
    protected final ServiceConfig serviceConfig;        // 来自防腐层
    
    public AbstractConfigurableStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig) {
        
        this.stepName = Objects.requireNonNull(stepName, "stepName cannot be null");
        this.stepConfig = stepConfig != null ? stepConfig : Map.of();
        this.serviceConfig = Objects.requireNonNull(serviceConfig, "serviceConfig cannot be null");
    }
    
    @Override
    public String getStepName() {
        return stepName;
    }
    
    /**
     * 从 stepConfig 中获取字符串配置值
     */
    protected String getConfigValue(String key, String defaultValue) {
        Object value = stepConfig.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }
    
    /**
     * 从 stepConfig 中获取整数配置值
     */
    protected int getConfigInt(String key, int defaultValue) {
        Object value = stepConfig.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 从 stepConfig 中获取布尔配置值
     */
    protected boolean getConfigBoolean(String key, boolean defaultValue) {
        Object value = stepConfig.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * 检查配置是否存在
     */
    protected boolean hasConfig(String key) {
        return stepConfig.containsKey(key);
    }
}
