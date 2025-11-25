package xyz.firestige.deploy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.config.stage.StageConfigurable;
import xyz.firestige.deploy.config.stage.ValidationResult;

import java.util.Map;

/**
 * Executor Stages 配置报告 (Phase4)
 *
 * <p>完全解耦的实现：
 * <ul>
 *   <li>自动发现所有阶段配置</li>
 *   <li>无需硬编码配置类列表</li>
 *   <li>新增配置类自动包含在报告中</li>
 * </ul>
 */
@Component
public class ExecutorStagesConfigurationReporter
        implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(
        ExecutorStagesConfigurationReporter.class);

    private final ExecutorStagesProperties properties;

    public ExecutorStagesConfigurationReporter(ExecutorStagesProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        printConfigurationReport();
    }

    /**
     * 打印配置报告
     */
    private void printConfigurationReport() {
        log.info("╔════════════════════════════════════════╗");
        log.info("║  Executor Stages 配置报告              ║");
        log.info("╚════════════════════════════════════════╝");

        // 自动遍历所有阶段配置
        Map<String, StageConfigurable> allStages = properties.getAllStages();

        if (allStages.isEmpty()) {
            log.warn("⚠ 未发现任何阶段配置");
            return;
        }

        allStages.forEach(this::reportStageConfig);

        // 统计信息
        long enabledCount = allStages.values().stream()
            .filter(StageConfigurable::isEnabled)
            .count();

        log.info("────────────────────────────────────────");
        log.info("总计: {} 个阶段, {} 个已启用, {} 个已禁用",
            allStages.size(),
            enabledCount,
            allStages.size() - enabledCount);
        log.info("════════════════════════════════════════");
    }

    /**
     * 报告单个阶段配置
     */
    private void reportStageConfig(String stageName, StageConfigurable config) {
        if (config == null) {
            log.warn("⚠ {}: 配置缺失（已使用默认配置）", stageName);
            return;
        }

        try {
            String status = config.isEnabled() ? "✓ 已启用" : "✗ 已禁用";
            String displayName = config.getStageName();
            String className = config.getClass().getSimpleName();

            log.info("  {} ({})", displayName, stageName);
            log.info("    状态: {}", status);
            log.info("    类型: {}", className);

            // 如果有验证警告，也打印出来
            if (config.isEnabled()) {
                ValidationResult validation = config.validate();

                if (!validation.getWarnings().isEmpty()) {
                    log.info("    警告:");
                    validation.getWarnings().forEach(warning ->
                        log.warn("      - {}", warning));
                }

                if (!validation.isValid()) {
                    log.info("    错误:");
                    validation.getErrors().forEach(error ->
                        log.error("      - {}", error));
                }
            }

        } catch (Exception e) {
            log.error("⚠ {}: 配置读取失败: {}", stageName, e.getMessage());
        }
    }
}

