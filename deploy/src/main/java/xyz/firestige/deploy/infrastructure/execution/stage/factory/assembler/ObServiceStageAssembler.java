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
import xyz.firestige.deploy.infrastructure.execution.stage.steps.PollingStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.RedisAckStep;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ResultValidator;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ValidationResult;
import xyz.firestige.deploy.domain.stage.model.ObConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OBService Stage 组装器
 *
 * @since RF-19-06
 */
@Component
@Order(40)
public class ObServiceStageAssembler implements StageAssembler {

    private static final Logger log = LoggerFactory.getLogger(ObServiceStageAssembler.class);

    @Override
    public String stageName() {
        return "ob-service";
    }

    @Override
    public boolean supports(TenantConfig cfg) {
        return cfg.getDeployUnit() != null;
    }

    @Override
    public TaskStage buildStage(TenantConfig cfg, SharedStageResources resources) {
        List<ConfigurableServiceStage.StepConfig> stepConfigs = new ArrayList<>();

        // Step 1: Polling (轮询 AgentService.judgeAgent)
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("ob-agent-polling")
            .dataPreparer(createOBPollingDataPreparer(cfg, resources))
            .step(new PollingStep("ob-agent-polling"))
            .resultValidator(createOBPollingValidator())
            .build());

        // Step 2: RedisAck (Write + Pub/Sub + Verify)
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("ob-redis-ack")
            .dataPreparer(createRedisAckDataPreparer(cfg, resources))
            .step(new RedisAckStep(resources.getRedisAckService()))
            .resultValidator(createRedisAckValidator())
            .build());

        return new ConfigurableServiceStage(stageName(), stepConfigs);
    }

    // ---- Polling ----

    private DataPreparer createOBPollingDataPreparer(TenantConfig config, SharedStageResources resources) {
        return (ctx) -> {
            int intervalMs = resources.getConfigLoader().getInfrastructure().getHealthCheck().getIntervalSeconds() * 1000;
            int maxAttempts = resources.getConfigLoader().getInfrastructure().getHealthCheck().getMaxAttempts();

            ctx.addVariable("pollInterval", intervalMs);
            ctx.addVariable("pollMaxAttempts", maxAttempts);

            ctx.addVariable("pollCondition", (PollingStep.PollCondition) (pollCtx) -> {
                Object agentService = pollCtx.getAdditionalData("agentService");
                if (agentService == null) {
                    log.warn("AgentService 未注入，OB 轮询跳过");
                    return true;
                }

                try {
                    var method = agentService.getClass().getMethod("judgeAgent", String.class, Long.class);
                    Boolean result = (Boolean) method.invoke(
                        agentService,
                        config.getTenantId().getValue(),
                        config.getPlanId().getValue()
                    );

                    log.debug("AgentService.judgeAgent() 返回: {}", result);
                    return result != null && result;

                } catch (Exception e) {
                    log.warn("调用 AgentService.judgeAgent() 异常: {}", e.getMessage());
                    return false;
                }
            });

            log.debug("OB Polling 数据准备完成: interval={}ms, maxAttempts={}", intervalMs, maxAttempts);
        };
    }

    private ResultValidator createOBPollingValidator() {
        return (ctx) -> {
            Boolean isReady = ctx.getAdditionalData("pollingResult", Boolean.class);
            if (isReady != null && isReady) {
                return ValidationResult.success("Agent 就绪");
            }
            return ValidationResult.failure("Agent 轮询超时");
        };
    }

    // ---- RedisAck DataPreparer & Validator ----

    /**
     * RedisAck 数据准备器
     * 整合了 Write、Pub/Sub、Verify 三个步骤
     */
    private DataPreparer createRedisAckDataPreparer(TenantConfig config, SharedStageResources resources) {
        return (ctx) -> {
            // 1. Redis Write 配置
            String redisKeyPrefix = resources.getConfigLoader().getInfrastructure().getRedis().getHashKeyPrefix();
            String redisKey = redisKeyPrefix + config.getTenantId().getValue();
            String redisField = "ob-campaign";

            // 2. 构建 ObConfig（包含业务数据和 metadata）
            ObConfig obConfig = new ObConfig(
                config.getTenantId().getValue(),
                extractSourceUnit(config),
                extractTargetUnit(config)
            );

            // 转换为 Map 以便添加 metadata
            Map<String, Object> redisValue = new HashMap<>();
            redisValue.put("tenantId", obConfig.getTenantId());
            redisValue.put("sourceUnitName", obConfig.getSourceUnitName());
            redisValue.put("targetUnitName", obConfig.getTargetUnitName());
            redisValue.put("timestamp", obConfig.getTimestamp());

            // 添加 metadata（包含 version 作为 footprint）
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("version", config.getPlanVersion());
            redisValue.put("metadata", metadata);

            // 3. Footprint（从 PlanVersion）
            String footprint = String.valueOf(config.getPlanVersion());

            // 4. Pub/Sub 配置
            String topic = resources.getConfigLoader().getInfrastructure().getRedis().getPubsubTopic();

            Map<String, Object> messageBody = new HashMap<>();
            messageBody.put("tenantId", config.getTenantId().getValue());
            messageBody.put("appName", "ob-campaign");
            messageBody.put("version", config.getPlanVersion());
            messageBody.put("timestamp", System.currentTimeMillis());

            String message;
            try {
                message = resources.getObjectMapper().writeValueAsString(messageBody);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize message", e);
            }

            // 5. Verify 配置
            List<String> endpoints = resolveEndpoints("obService", "ob-service", resources);
            String healthCheckPath = extractHealthCheckPath(config, resources);
            List<String> verifyUrls = endpoints.stream()
                .map(ep -> "http://" + ep + healthCheckPath)
                .collect(Collectors.toList());

            int maxAttempts = resources.getConfigLoader().getInfrastructure().getHealthCheck().getMaxAttempts();
            int intervalSec = resources.getConfigLoader().getInfrastructure().getHealthCheck().getIntervalSeconds();

            // 6. 放入 Context
            ctx.addVariable("redisKey", redisKey);
            ctx.addVariable("redisField", redisField);
            ctx.addVariable("redisValue", redisValue);
            ctx.addVariable("footprint", footprint);
            ctx.addVariable("pubsubTopic", topic);
            ctx.addVariable("pubsubMessage", message);
            ctx.addVariable("verifyUrls", verifyUrls);
            ctx.addVariable("verifyJsonPath", "$.metadata.version");
            ctx.addVariable("retryMaxAttempts", maxAttempts);
            ctx.addVariable("retryDelay", Duration.ofSeconds(intervalSec));
            ctx.addVariable("timeout", Duration.ofSeconds(maxAttempts * intervalSec + 10));

            log.debug("OB RedisAck 数据准备完成: key={}, field={}, endpoints={}, version={}",
                redisKey, redisField, verifyUrls.size(), footprint);
        };
    }

    /**
     * RedisAck 结果验证器
     */
    private ResultValidator createRedisAckValidator() {
        return (ctx) -> {
            // 1. 优先检查 FailureInfo
            xyz.firestige.deploy.domain.shared.exception.FailureInfo failureInfo =
                ctx.getAdditionalData("failureInfo", xyz.firestige.deploy.domain.shared.exception.FailureInfo.class);
            if (failureInfo != null) {
                return ValidationResult.failure(failureInfo.getErrorMessage());
            }

            // 2. 检查 AckResult
            xyz.firestige.redis.ack.api.AckResult result =
                ctx.getAdditionalData("ackResult", xyz.firestige.redis.ack.api.AckResult.class);

            if (result == null) {
                return ValidationResult.failure("未获取到 ACK 结果");
            }

            if (result.isSuccess()) {
                return ValidationResult.success(
                    String.format("OB 配置推送并验证成功（尝试 %d 次，耗时 %s）",
                        result.getAttempts(),
                        result.getElapsed())
                );
            }

            if (result.isTimeout()) {
                return ValidationResult.failure(
                    String.format("OB 验证超时（尝试 %d 次，耗时 %s）",
                        result.getAttempts(),
                        result.getElapsed())
                );
            }

            if (result.isFootprintMismatch()) {
                return ValidationResult.failure(
                    String.format("OB 版本不匹配（期望：%s，实际：%s）",
                        result.getExpectedFootprint(),
                        result.getActualFootprint())
                );
            }

            return ValidationResult.failure("OB ACK 失败：" + result.getReason());
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

