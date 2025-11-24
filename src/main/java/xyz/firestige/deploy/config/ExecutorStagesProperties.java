package xyz.firestige.deploy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import xyz.firestige.deploy.config.stage.StageConfigurable;
import xyz.firestige.deploy.config.stage.StageConfigUtils;
import xyz.firestige.deploy.config.stage.ValidationResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 执行阶段配置容器 (Phase2 实现)
 * prefix: executor.stages
 */
@ConfigurationProperties(prefix = "executor.stages")
public class ExecutorStagesProperties implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(ExecutorStagesProperties.class);

    private final Map<String, StageConfigurable> stages = new LinkedHashMap<>();

    @NestedConfigurationProperty
    private BlueGreenGatewayStageConfig blueGreenGateway;
    @NestedConfigurationProperty
    private PortalStageConfig portal;
    @NestedConfigurationProperty
    private ASBCGatewayStageConfig asbcGateway;

    @Override
    public void afterPropertiesSet() {
        log.info("[ExecutorStagesProperties] 初始化阶段配置开始");
        registerStageConfigurations();
        validateAllConfigurations();
        log.info("[ExecutorStagesProperties] 初始化完成: 总阶段 {} 已启用 {}", stages.size(),
                stages.values().stream().filter(StageConfigurable::isEnabled).count());
    }

    private void registerStageConfigurations() {
        try {
            Field[] fields = this.getClass().getDeclaredFields();
            int registered = 0;
            for (Field field : fields) {
                if (shouldSkipField(field)) continue;
                field.setAccessible(true);
                Object value = field.get(this);
                if (!StageConfigurable.class.isAssignableFrom(field.getType())) continue;
                if (value == null) {
                    value = createDefaultConfig(field.getType());
                    field.set(this, value);
                }
                if (value != null) {
                    StageConfigurable config = (StageConfigurable) value;
                    String stageName = StageConfigUtils.toKebabCase(field.getName());
                    stages.put(stageName, config);
                    log.debug("注册阶段配置: {} -> {} (enabled={})", stageName, config.getClass().getSimpleName(), config.isEnabled());
                    registered++;
                }
            }
            log.info("已注册阶段配置 {} 个", registered);
        } catch (Exception e) {
            log.error("阶段配置注册失败: {}", e.getMessage(), e);
        }
    }

    private boolean shouldSkipField(Field field) {
        String name = field.getName();
        return name.equals("stages") || name.equals("log") || field.isSynthetic();
    }

    private StageConfigurable createDefaultConfig(Class<?> type) {
        try {
            try {
                Method m = type.getMethod("defaultConfig");
                return (StageConfigurable) m.invoke(null);
            } catch (NoSuchMethodException ex) {
                return (StageConfigurable) type.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            log.warn("无法创建默认配置: {} - {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private void validateAllConfigurations() {
        int ok = 0, warn = 0, err = 0;
        for (Map.Entry<String, StageConfigurable> entry : stages.entrySet()) {
            String name = entry.getKey();
            StageConfigurable cfg = entry.getValue();
            try {
                ValidationResult result = cfg.validate();
                if (!result.isValid()) {
                    err++;
                    log.error("阶段 {} 配置错误: {}", name, String.join("; ", result.getErrors()));
                } else {
                    ok++;
                }
                if (!result.getWarnings().isEmpty()) {
                    warn++;
                    log.warn("阶段 {} 配置警告: {}", name, String.join("; ", result.getWarnings()));
                }
            } catch (Exception e) {
                err++;
                log.error("阶段 {} 验证异常: {}", name, e.getMessage(), e);
            }
        }
        log.info("阶段配置验证结果：成功 {} 警告 {} 错误 {}", ok, warn, err);
    }

    // Public accessors
    public Map<String, StageConfigurable> getAllStages() {
        return Collections.unmodifiableMap(stages);
    }

    public Map<String, StageConfigurable> getEnabledStages() {
        return stages.entrySet().stream()
                .filter(e -> e.getValue().isEnabled())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a,b)->a, LinkedHashMap::new));
    }

    public <T extends StageConfigurable> T getStage(String stageName, Class<T> type) {
        StageConfigurable cfg = stages.get(stageName);
        return cfg != null ? type.cast(cfg) : null;
    }

    public boolean isStageEnabled(String stageName) {
        StageConfigurable cfg = stages.get(stageName);
        return cfg != null && cfg.isEnabled();
    }

    // Getters / setters for binding compatibility
    public BlueGreenGatewayStageConfig getBlueGreenGateway() { return blueGreenGateway; }
    public void setBlueGreenGateway(BlueGreenGatewayStageConfig blueGreenGateway) { this.blueGreenGateway = blueGreenGateway; }
    public PortalStageConfig getPortal() { return portal; }
    public void setPortal(PortalStageConfig portal) { this.portal = portal; }
    public ASBCGatewayStageConfig getAsbcGateway() { return asbcGateway; }
    public void setAsbcGateway(ASBCGatewayStageConfig asbcGateway) { this.asbcGateway = asbcGateway; }
}

