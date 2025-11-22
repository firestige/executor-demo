package xyz.firestige.deploy.infrastructure.execution.stage.factory.assembler;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.infrastructure.execution.stage.ConfigurableServiceStage;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.SharedStageResources;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.StageAssembler;
import xyz.firestige.deploy.infrastructure.execution.stage.preparer.DataPreparer;
import xyz.firestige.deploy.infrastructure.execution.stage.redis.ConfigWriteResult;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.ConfigWriteStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.MessageBroadcastStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.PollingStep;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ResultValidator;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ValidationResult;

import java.util.*;

/**
 * Blue-Green Gateway Stage 组装器
 *
 * @since RF-19-06
 */
@Component
@Order(30)
public class BlueGreenStageAssembler implements StageAssembler {

    private static final Logger log = LoggerFactory.getLogger(BlueGreenStageAssembler.class);

    @Override
    public String stageName() {
        return "blue-green-gateway";
    }

    @Override
    public boolean supports(TenantConfig cfg) {
        return cfg.getRouteRules() != null && !cfg.getRouteRules().isEmpty();
    }

    @Override
    public TaskStage buildStage(TenantConfig cfg, SharedStageResources resources) {
        List<ConfigurableServiceStage.StepConfig> stepConfigs = new ArrayList<>();

        // Step 1: Redis Config Write
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("bg-config-write")
            .dataPreparer(createBGConfigWriteDataPreparer(cfg, resources))
            .step(new ConfigWriteStep(resources.getRedisTemplate()))
            .resultValidator(createBGConfigWriteValidator())
            .build());

        // Step 2: Redis Pub/Sub Broadcast
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("bg-message-broadcast")
            .dataPreparer(createBGMessageBroadcastDataPreparer(cfg, resources))
            .step(new MessageBroadcastStep(resources.getRedisTemplate()))
            .resultValidator(createBGMessageBroadcastValidator())
            .build());

        // Step 3: Health Check Polling
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("bg-health-check")
            .dataPreparer(createBGHealthCheckDataPreparer(cfg, resources))
            .step(new PollingStep("bg-health-check"))
            .resultValidator(createBGHealthCheckValidator())
            .build());

        return new ConfigurableServiceStage(stageName(), stepConfigs);
    }

    // ---- ConfigWrite ----

    private DataPreparer createBGConfigWriteDataPreparer(TenantConfig config, SharedStageResources resources) {
        return (ctx) -> {
            String redisKeyPrefix = resources.getConfigLoader().getInfrastructure().getRedis().getHashKeyPrefix();
            String key = redisKeyPrefix + config.getTenantId().getValue();
            String field = "icc-bg-gateway";

            Map<String, Object> redisValue = new HashMap<>();
            redisValue.put("tenantId", config.getTenantId().getValue());
            redisValue.put("sourceUnit", extractSourceUnit(config));
            redisValue.put("targetUnit", extractTargetUnit(config));
            redisValue.put("routes", convertRouteRulesToMap(config));

            String value;
            try {
                value = resources.getObjectMapper().writeValueAsString(redisValue);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize Redis value", e);
            }

            ctx.addVariable("key", key);
            ctx.addVariable("field", field);
            ctx.addVariable("value", value);

            log.debug("BG ConfigWrite 数据准备完成: key={}, field={}", key, field);
        };
    }

    private ResultValidator createBGConfigWriteValidator() {
        return (ctx) -> {
            ConfigWriteResult result = ctx.getAdditionalData("configWriteResult", ConfigWriteResult.class);
            if (result != null && result.isSuccess()) {
                return ValidationResult.success("配置写入成功");
            }
            return ValidationResult.failure("配置写入失败");
        };
    }

    // ---- MessageBroadcast ----

    private DataPreparer createBGMessageBroadcastDataPreparer(TenantConfig config, SharedStageResources resources) {
        return (ctx) -> {
            String topic = resources.getConfigLoader().getInfrastructure().getRedis().getPubsubTopic();

            Map<String, Object> messageBody = new HashMap<>();
            messageBody.put("tenantId", config.getTenantId().getValue());
            messageBody.put("appName", "icc-bg-gateway");
            messageBody.put("timestamp", System.currentTimeMillis());

            String message;
            try {
                message = resources.getObjectMapper().writeValueAsString(messageBody);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize message", e);
            }

            ctx.addVariable("topic", topic);
            ctx.addVariable("message", message);

            log.debug("BG MessageBroadcast 数据准备完成: topic={}", topic);
        };
    }

    private ResultValidator createBGMessageBroadcastValidator() {
        return (ctx) -> ValidationResult.success("消息广播成功");
    }

    // ---- HealthCheck ----

    private DataPreparer createBGHealthCheckDataPreparer(TenantConfig config, SharedStageResources resources) {
        return (ctx) -> {
            int intervalMs = resources.getConfigLoader().getInfrastructure().getHealthCheck().getIntervalSeconds() * 1000;
            int maxAttempts = resources.getConfigLoader().getInfrastructure().getHealthCheck().getMaxAttempts();
            String healthCheckPath = extractHealthCheckPath(config, resources);

            List<String> endpoints = resolveEndpoints("blueGreenGatewayService", "blue-green-gateway", resources);
            List<String> healthCheckUrls = new ArrayList<>();
            for (String endpoint : endpoints) {
                healthCheckUrls.add("http://" + endpoint + healthCheckPath);
            }

            ctx.addVariable("pollInterval", intervalMs);
            ctx.addVariable("pollMaxAttempts", maxAttempts);

            ctx.addVariable("pollCondition", (PollingStep.PollCondition) (pollCtx) -> {
                for (String url : healthCheckUrls) {
                    try {
                        String response = resources.getRestTemplate().getForObject(url, String.class);
                        if (response == null || !response.contains("version")) {
                            log.debug("健康检查未通过: url={}", url);
                            return false;
                        }
                    } catch (Exception e) {
                        log.debug("健康检查异常: url={}, error={}", url, e.getMessage());
                        return false;
                    }
                }
                return true;
            });

            log.debug("BG HealthCheck 数据准备完成: endpoints={}, interval={}ms, maxAttempts={}",
                healthCheckUrls.size(), intervalMs, maxAttempts);
        };
    }

    private ResultValidator createBGHealthCheckValidator() {
        return (ctx) -> {
            Boolean isHealthy = ctx.getAdditionalData("pollingResult", Boolean.class);
            if (isHealthy != null && isHealthy) {
                return ValidationResult.success("所有实例健康检查通过");
            }
            return ValidationResult.failure("健康检查失败");
        };
    }

    // ---- 辅助方法 ----

    private String extractSourceUnit(TenantConfig config) {
        if (config.getPreviousConfig() != null && config.getPreviousConfig().getDeployUnit() != null) {
            return config.getPreviousConfig().getDeployUnit().name();
        }
        return extractTargetUnit(config);
    }

    private String extractTargetUnit(TenantConfig config) {
        if (config.getDeployUnit() != null) {
            return config.getDeployUnit().name();
        }
        throw new IllegalArgumentException("deployUnit.name is required");
    }

    private List<Map<String, String>> convertRouteRulesToMap(TenantConfig config) {
        List<Map<String, String>> routes = new ArrayList<>();
        if (config.getRouteRules() != null) {
            for (var rule : config.getRouteRules()) {
                Map<String, String> routeMap = new HashMap<>();
                routeMap.put("id", rule.id());
                routeMap.put("sourceUri", rule.sourceUri().toString());
                routeMap.put("targetUri", rule.targetUri().toString());
                routes.add(routeMap);
            }
        }
        return routes;
    }

    private String extractHealthCheckPath(TenantConfig config, SharedStageResources resources) {
        if (config.getHealthCheckEndpoints() != null && !config.getHealthCheckEndpoints().isEmpty()) {
            return config.getHealthCheckEndpoints().get(0);
        }
        String template = resources.getConfigLoader().getInfrastructure().getHealthCheck().getDefaultPath();
        return template.replace("{tenantId}", config.getTenantId().getValue());
    }

    private List<String> resolveEndpoints(String nacosServiceKey, String fallbackKey, SharedStageResources resources) {
        List<String> fallbackInstances = resources.getConfigLoader().getInfrastructure()
            .getFallbackInstances()
            .get(fallbackKey);

        if (fallbackInstances == null || fallbackInstances.isEmpty()) {
            throw new IllegalStateException("No fallback instances configured for: " + fallbackKey);
        }

        log.debug("使用 fallback 实例: service={}, count={}", fallbackKey, fallbackInstances.size());
        return fallbackInstances;
    }
}

