package xyz.firestige.deploy.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.config.ExecutorStagesProperties;
import xyz.firestige.deploy.config.stage.StageConfigurable;
import xyz.firestige.deploy.config.stage.ValidationResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Executor Stages 健康检查 (Phase4)
 *
 * <p>完全解耦的实现：
 * <ul>
 *   <li>自动发现所有阶段配置</li>
 *   <li>无需硬编码配置类列表</li>
 *   <li>新增配置类自动包含在健康检查中</li>
 * </ul>
 */
@Component
@ConditionalOnClass(HealthIndicator.class)
public class ExecutorStagesHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ExecutorStagesHealthIndicator.class);

    private final ExecutorStagesProperties properties;

    public ExecutorStagesHealthIndicator(ExecutorStagesProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            Map<String, Object> details = new HashMap<>();

            // 遍历所有阶段配置（自动发现，无需硬编码）
            Map<String, StageConfigurable> allStages = properties.getAllStages();

            if (allStages.isEmpty()) {
                return Health.down()
                    .withDetail("message", "未发现任何阶段配置")
                    .build();
            }

            // 检查每个阶段配置
            allStages.forEach((stageName, config) -> {
                details.put(stageName, checkStageConfig(config));
            });

            // 统计信息
            long enabledCount = allStages.values().stream()
                .filter(StageConfigurable::isEnabled)
                .count();

            details.put("summary", Map.of(
                "totalStages", allStages.size(),
                "enabledStages", enabledCount,
                "disabledStages", allStages.size() - enabledCount
            ));

            // 判断健康状态
            boolean hasErrors = details.values().stream()
                .filter(v -> v instanceof Map)
                .anyMatch(v -> "ERROR".equals(((Map<?, ?>) v).get("status")));

            boolean hasWarnings = details.values().stream()
                .filter(v -> v instanceof Map)
                .anyMatch(v -> "WARNING".equals(((Map<?, ?>) v).get("status")));

            if (hasErrors) {
                return Health.down()
                    .withDetail("message", "部分配置存在错误")
                    .withDetails(details)
                    .build();
            }

            if (hasWarnings) {
                return Health.status("WARNING")
                    .withDetail("message", "部分配置存在警告，但应用可正常运行")
                    .withDetails(details)
                    .build();
            }

            return Health.up()
                .withDetail("message", "所有配置正常")
                .withDetails(details)
                .build();

        } catch (Exception e) {
            log.error("健康检查异常", e);
            return Health.down()
                .withException(e)
                .withDetail("message", "健康检查异常，但应用仍可运行")
                .build();
        }
    }

    /**
     * 检查单个阶段配置
     */
    private Map<String, Object> checkStageConfig(StageConfigurable config) {
        Map<String, Object> result = new HashMap<>();

        if (config == null) {
            result.put("status", "WARNING");
            result.put("message", "配置缺失，已使用默认配置");
            return result;
        }

        try {
            result.put("status", "OK");
            result.put("enabled", config.isEnabled());
            result.put("displayName", config.getStageName());

            if (!config.isEnabled()) {
                result.put("message", "已禁用");
                return result;
            }

            // 执行配置验证
            ValidationResult validation = config.validate();

            if (!validation.getWarnings().isEmpty()) {
                result.put("status", "WARNING");
                result.put("warnings", validation.getWarnings());
            }

            if (!validation.isValid()) {
                result.put("status", "ERROR");
                result.put("errors", validation.getErrors());
            }

        } catch (Exception e) {
            log.error("检查配置失败: {}", config.getClass().getSimpleName(), e);
            result.put("status", "ERROR");
            result.put("message", "配置检查失败: " + e.getMessage());
        }

        return result;
    }
}

