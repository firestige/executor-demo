package xyz.firestige.deploy.infrastructure.execution.stage.factory.assembler;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.infrastructure.discovery.SelectionStrategy;
import xyz.firestige.deploy.infrastructure.execution.stage.ConfigurableServiceStage;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.SharedStageResources;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.StageAssembler;
import xyz.firestige.deploy.infrastructure.execution.stage.preparer.DataPreparer;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.RedisAckStep;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ResultValidator;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ValidationResult;
import xyz.firestige.redis.ack.api.AckResult;

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

        // 单一 Step：RedisAck 一体化流程（Write + Pub/Sub + Verify）
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("bg-redis-ack")
            .dataPreparer(createRedisAckDataPreparer(cfg, resources))
            .step(new RedisAckStep(resources.getRedisAckService()))
            .resultValidator(createRedisAckValidator())
            .build());

        return new ConfigurableServiceStage(stageName(), stepConfigs);
    }

    // ---- RedisAck DataPreparer & Validator ----

    /**
     * RedisAck 数据准备器
     * 整合了原有的 ConfigWrite、MessageBroadcast、HealthCheck 三个步骤的数据准备
     */
    private DataPreparer createRedisAckDataPreparer(TenantConfig config, SharedStageResources resources) {
        return (ctx) -> {
            // 1. Redis Write 配置
            String redisKeyPrefix = resources.getRedisHashKeyPrefix();
            String redisKey = redisKeyPrefix + config.getTenantId().getValue();
            String redisField = "icc-bg-gateway";

            // 2. 构建完整的 Redis Value（包含业务数据）
            Map<String, Object> redisValue = new HashMap<>();
            redisValue.put("tenantId", config.getTenantId().getValue());
            redisValue.put("sourceUnit", extractSourceUnit(config));
            redisValue.put("targetUnit", extractTargetUnit(config));
            redisValue.put("routes", convertRouteRulesToMap(config));

            // 3. 构建 metadata 对象（包含 version 作为 footprint）
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("version", config.getPlanVersion());
            redisValue.put("metadata", metadata);

            // 4. Footprint（从 PlanVersion）
            String footprint = String.valueOf(config.getPlanVersion());

            // 5. Pub/Sub 配置
            String topic = resources.getRedisPubsubTopic();

            Map<String, Object> messageBody = new HashMap<>();
            messageBody.put("tenantId", config.getTenantId().getValue());
            messageBody.put("appName", "icc-bg-gateway");
            messageBody.put("version", config.getPlanVersion());
            messageBody.put("timestamp", System.currentTimeMillis());

            String message;
            try {
                message = resources.getObjectMapper().writeValueAsString(messageBody);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize message", e);
            }

            // 6. Verify 配置 - 使用 ServiceDiscoveryHelper
            String namespace = config.getNacosNameSpace();  // 从 TenantConfig 获取 namespace
            List<String> endpoints = resolveEndpoints(
                "blueGreenGatewayService",
                namespace,
                SelectionStrategy.ALL,  // BG 需要全部实例并发验证
                true,  // 启用健康检查
                resources
            );
            String healthCheckPath = extractHealthCheckPath(config, resources);
            List<String> verifyUrls = endpoints.stream()
                .map(ep -> "http://" + ep + healthCheckPath)
                .collect(java.util.stream.Collectors.toList());

            int maxAttempts = resources.getVerifyMaxAttempts();
            int intervalSec = resources.getVerifyIntervalSeconds();

            // 7. 放入 Context
            ctx.addVariable("redisKey", redisKey);
            ctx.addVariable("redisField", redisField);
            ctx.addVariable("redisValue", redisValue);
            ctx.addVariable("footprint", footprint);
            ctx.addVariable("pubsubTopic", topic);
            ctx.addVariable("pubsubMessage", message);
            ctx.addVariable("verifyUrls", verifyUrls);
            ctx.addVariable("verifyJsonPath", "$.metadata.version");
            ctx.addVariable("retryMaxAttempts", maxAttempts);
            ctx.addVariable("retryDelay", java.time.Duration.ofSeconds(intervalSec));
            ctx.addVariable("timeout", java.time.Duration.ofSeconds(maxAttempts * intervalSec + 10));

            log.debug("BG RedisAck 数据准备完成: key={}, field={}, endpoints={}, version={}",
                redisKey, redisField, verifyUrls.size(), footprint);
        };
    }

    /**
     * RedisAck 结果验证器
     */
    private ResultValidator createRedisAckValidator() {
        return (ctx) -> {
            // 1. 优先检查 FailureInfo
            FailureInfo failureInfo =
                ctx.getAdditionalData("failureInfo", FailureInfo.class);
            if (failureInfo != null) {
                return ValidationResult.failure(failureInfo.getErrorMessage());
            }

            // 2. 检查 AckResult
            AckResult result =
                ctx.getAdditionalData("ackResult", AckResult.class);

            if (result == null) {
                return ValidationResult.failure("未获取到 ACK 结果");
            }

            if (result.isSuccess()) {
                return ValidationResult.success(
                    String.format("配置推送并验证成功（尝试 %d 次，耗时 %s）",
                        result.getAttempts(),
                        result.getElapsed())
                );
            }

            if (result.isTimeout()) {
                return ValidationResult.failure(
                    String.format("验证超时（尝试 %d 次，耗时 %s）",
                        result.getAttempts(),
                        result.getElapsed())
                );
            }

            if (result.isFootprintMismatch()) {
                return ValidationResult.failure(
                    String.format("版本不匹配（期望：%s，实际：%s）",
                        result.getExpectedFootprint(),
                        result.getActualFootprint())
                );
            }

            return ValidationResult.failure("ACK 失败：" + result.getReason());
        };
    }

    // ---- 原有的辅助方法保留（用于数据准备）----

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
        String template = resources.getVerifyDefaultPath();
        return template.replace("{tenantId}", config.getTenantId().getValue());
    }

    /**
     * 解析服务实例端点
     *
     * @param serviceKey 服务标识
     * @param namespace Nacos 命名空间（从 TenantConfig 获取）
     * @param strategy 实例选择策略
     * @param enableHealthCheck 是否启用健康检查
     * @param resources 共享资源
     * @return 实例列表（host:port）
     */
    private List<String> resolveEndpoints(String serviceKey,
                                          String namespace,
                                          SelectionStrategy strategy,
                                          boolean enableHealthCheck,
                                          SharedStageResources resources) {
        try {
            return resources.getServiceDiscoveryHelper()
                .selectInstances(serviceKey, namespace, strategy, enableHealthCheck);
        } catch (Exception e) {
            log.error("服务发现失败: service={}, namespace={}", serviceKey, namespace, e);
            throw new IllegalStateException("Failed to resolve service instances: " + serviceKey, e);
        }
    }
}

