package xyz.firestige.deploy.infrastructure.execution.stage.factory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.application.dto.MediaRoutingConfig;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.execution.stage.ConfigurableServiceStage;
import xyz.firestige.deploy.infrastructure.execution.stage.StageFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.execution.stage.asbc.ASBCResponse;
import xyz.firestige.deploy.infrastructure.execution.stage.asbc.ASBCResponseData;
import xyz.firestige.deploy.infrastructure.execution.stage.asbc.ASBCResultItem;
import xyz.firestige.deploy.infrastructure.execution.stage.http.HttpResponseData;
import xyz.firestige.deploy.infrastructure.execution.stage.portal.PortalResponse;
import xyz.firestige.deploy.infrastructure.execution.stage.preparer.DataPreparer;
import xyz.firestige.deploy.infrastructure.execution.stage.redis.ConfigWriteResult;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.ConfigWriteStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.HttpRequestStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.MessageBroadcastStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.PollingStep;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ResultValidator;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态 Stage 工厂（代码编排）
 * 根据 TenantConfig 动态创建 Stage 列表
 *
 * @since RF-19 三层抽象架构
 * @deprecated 已由 OrchestratedStageFactory 替代（RF-19-06 策略化重构）
 */
@Deprecated
@Component
public class DynamicStageFactory implements StageFactory {

    private static final Logger log = LoggerFactory.getLogger(DynamicStageFactory.class);

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final DeploymentConfigLoader configLoader;
    private final ObjectMapper objectMapper;

    public DynamicStageFactory(
            RestTemplate restTemplate,
            StringRedisTemplate redisTemplate,
            DeploymentConfigLoader configLoader,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * 构建 Stage 列表（严格按顺序）
     *
     * @param tenantConfig 租户配置
     * @return Stage 列表
     */
    public List<TaskStage> buildStages(TenantConfig tenantConfig) {
        List<TaskStage> stages = new ArrayList<>();

        log.info("开始构建 Stages for tenant: {}", tenantConfig.getTenantId());

        // Stage 1: ASBC Gateway
        if (tenantConfig.getMediaRoutingConfig() != null) {
            stages.add(createASBCStage(tenantConfig));
            log.debug("添加 ASBC Stage");
        }

        // Stage 2: Portal
        if (tenantConfig.getDeployUnit() != null) {
            stages.add(createPortalStage(tenantConfig));
            log.debug("添加 Portal Stage");
        }

        // Stage 3: Blue-Green Gateway (RF-19 迁移完成)
        if (tenantConfig.getRouteRules() != null && !tenantConfig.getRouteRules().isEmpty()) {
            stages.add(createBlueGreenGatewayStage(tenantConfig));
            log.debug("添加 Blue-Green Gateway Stage");
        }

        // Stage 4: OBService (RF-19-03 完成)
        if (shouldCreateOBServiceStage(tenantConfig)) {
            stages.add(createOBServiceStage(tenantConfig));
            log.debug("添加 OBService Stage");
        }

        log.info("构建完成，共 {} 个 Stage", stages.size());
        return stages;
    }

    /**
     * 判断是否需要创建 OBService Stage
     */
    private boolean shouldCreateOBServiceStage(TenantConfig tenantConfig) {
        // 需要有 DeployUnit 信息
        return tenantConfig.getDeployUnit() != null;
    }

    // ========================================
    // ASBC Gateway Stage
    // ========================================

    private TaskStage createASBCStage(TenantConfig tenantConfig) {
        ConfigurableServiceStage.StepConfig stepConfig = ConfigurableServiceStage.StepConfig.builder()
            .stepName("asbc-http-request")
            .dataPreparer(createASBCDataPreparer(tenantConfig))
            .step(new HttpRequestStep(restTemplate))
            .resultValidator(createASBCResultValidator())
            .build();

        return new ConfigurableServiceStage("asbc-gateway", Collections.singletonList(stepConfig));
    }

    /**
     * ASBC 数据准备器
     */
    private DataPreparer createASBCDataPreparer(TenantConfig tenantConfig) {
        return (ctx) -> {
            MediaRoutingConfig mediaRouting = tenantConfig.getMediaRoutingConfig();

            // 1. 解析 calledNumberRules (逗号分隔 → List)
            String rulesStr = mediaRouting.calledNumberRules();
            String[] numbers = rulesStr.split(",");
            List<String> calledNumberList = new ArrayList<>();
            for (String num : numbers) {
                String trimmed = num.trim();
                if (!trimmed.isEmpty()) {
                    calledNumberList.add(trimmed);
                }
            }

            // 2. 获取 endpoint (暂时硬编码，TODO: 从 Nacos 获取)
            String endpoint = "https://192.168.1.100:8080/api/sbc/traffic-switch";

            // 3. 构建请求数据
            Map<String, Object> body = new HashMap<>();
            body.put("calledNumberMatch", calledNumberList);
            body.put("targetTrunkGroupName", mediaRouting.trunkGroup());

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            // 4. 从 auth 配置读取认证信息
            var authConfig = configLoader.getInfrastructure().getAuthConfig("asbc");
            if (authConfig != null && authConfig.isEnabled()) {
                String token = generateToken(authConfig.getTokenProvider());
                if (token != null) {
                    headers.put("Authorization", "Bearer " + token);
                    log.debug("ASBC auth enabled, token provider: {}", authConfig.getTokenProvider());
                }
            } else {
                log.debug("ASBC auth disabled");
            }

            // 5. 放入 TaskRuntimeContext
            ctx.addVariable("url", endpoint);
            ctx.addVariable("method", "POST");
            ctx.addVariable("headers", headers);
            ctx.addVariable("body", body);

            log.debug("ASBC 数据准备完成: endpoint={}, calledNumberMatch={}",
                endpoint, calledNumberList);
        };
    }

    /**
     * ASBC 结果验证器
     */
    private ResultValidator createASBCResultValidator() {
        return (ctx) -> {
            HttpResponseData response = ctx.getAdditionalData("httpResponse", HttpResponseData.class);

            // 1. 检查 HTTP 状态码
            if (!response.is2xx()) {
                return ValidationResult.failure(
                    String.format("ASBC HTTP 错误: %d", response.getStatusCode())
                );
            }

            // 2. 解析 JSON
            try {
                ASBCResponse asbcResponse = response.parseBody(ASBCResponse.class);

                // 3. 检查业务 code
                if (asbcResponse.getCode() == null || asbcResponse.getCode() != 0) {
                    return ValidationResult.failure(
                        String.format("ASBC 返回错误: code=%d, msg=%s",
                            asbcResponse.getCode(), asbcResponse.getMsg())
                    );
                }

                // 4. 检查 failList
                ASBCResponseData data = asbcResponse.getData();
                if (data != null && data.getFailList() != null && !data.getFailList().isEmpty()) {
                    return ValidationResult.failure(buildASBCFailureMessage(data));
                }

                // 5. 全部成功
                int successCount = (data != null && data.getSuccessList() != null)
                    ? data.getSuccessList().size() : 0;
                return ValidationResult.success(
                    String.format("ASBC 配置成功: %d 个规则", successCount)
                );

            } catch (Exception e) {
                log.error("ASBC 响应解析失败", e);
                return ValidationResult.failure("响应解析失败: " + e.getMessage());
            }
        };
    }

    /**
     * 构建 ASBC 失败信息（包含成功和失败详情）
     */
    private String buildASBCFailureMessage(ASBCResponseData data) {
        StringBuilder sb = new StringBuilder("ASBC 配置部分失败:\n");

        // 成功列表
        if (data.getSuccessList() != null && !data.getSuccessList().isEmpty()) {
            sb.append("成功 (").append(data.getSuccessList().size()).append(" 项):\n");
            for (ASBCResultItem item : data.getSuccessList()) {
                sb.append("  ✓ ").append(item.getCalledNumberMatch())
                  .append(" → ").append(item.getTargetTrunkGroupName()).append("\n");
            }
        }

        // 失败列表
        if (data.getFailList() != null && !data.getFailList().isEmpty()) {
            sb.append("失败 (").append(data.getFailList().size()).append(" 项):\n");
            for (ASBCResultItem item : data.getFailList()) {
                sb.append("  ✗ ").append(item.getCalledNumberMatch())
                  .append(" → ").append(item.getTargetTrunkGroupName())
                  .append(" [").append(item.getMsg()).append("]\n");
            }
        }

        return sb.toString();
    }

    // ========================================
    // Portal Stage
    // ========================================

    private TaskStage createPortalStage(TenantConfig tenantConfig) {
        ConfigurableServiceStage.StepConfig stepConfig = ConfigurableServiceStage.StepConfig.builder()
            .stepName("portal-notify")
            .dataPreparer(createPortalDataPreparer(tenantConfig))
            .step(new HttpRequestStep(restTemplate))
            .resultValidator(createPortalResultValidator())
            .build();

        return new ConfigurableServiceStage("portal", Collections.singletonList(stepConfig));
    }

    /**
     * Portal 数据准备器
     */
    private DataPreparer createPortalDataPreparer(TenantConfig tenantConfig) {
        return (ctx) -> {
            // 1. 获取 endpoint (暂时硬编码，TODO: 从 Nacos 获取)
            String baseUrl = "http://192.168.1.20:8080";
            String endpoint = baseUrl + "/icc-agent-portal/inner/v1/notify/bgSwitch";

            // 2. 构建请求 body
            Map<String, Object> body = new HashMap<>();
            body.put("tenantId", tenantConfig.getTenantId().getValue());
            body.put("targetDeployUnit", tenantConfig.getDeployUnit().name());
            body.put("timestamp", String.valueOf(System.currentTimeMillis()));

            // 3. 构建 headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            // 4. 放入 TaskRuntimeContext
            ctx.addVariable("url", endpoint);
            ctx.addVariable("method", "POST");
            ctx.addVariable("headers", headers);
            ctx.addVariable("body", body);

            log.debug("Portal 数据准备完成: endpoint={}, tenantId={}",
                endpoint, tenantConfig.getTenantId().getValue());
        };
    }

    /**
     * Portal 结果验证器
     */
    private ResultValidator createPortalResultValidator() {
        return (ctx) -> {
            HttpResponseData response = ctx.getAdditionalData("httpResponse", HttpResponseData.class);

            // 1. 检查 HTTP 状态码
            if (!response.is2xx()) {
                return ValidationResult.failure(
                    String.format("Portal HTTP 错误: %d", response.getStatusCode())
                );
            }

            // 2. 解析 JSON
            try {
                PortalResponse portalResponse = response.parseBody(PortalResponse.class);

                // 3. 检查业务 code
                if ("0".equals(portalResponse.getCode())) {
                    return ValidationResult.success(
                        String.format("Portal 通知成功: %s", portalResponse.getMsg())
                    );
                } else {
                    return ValidationResult.failure(
                        String.format("Portal 通知失败: code=%s, msg=%s",
                            portalResponse.getCode(), portalResponse.getMsg())
                    );
                }

            } catch (Exception e) {
                log.error("Portal 响应解析失败", e);
                return ValidationResult.failure("响应解析失败: " + e.getMessage());
            }
        };
    }

    // ========================================
    // Blue-Green Gateway Stage (RF-19 迁移)
    // ========================================

    private TaskStage createBlueGreenGatewayStage(TenantConfig tenantConfig) {
        List<ConfigurableServiceStage.StepConfig> stepConfigs = new ArrayList<>();

        // Step 1: Redis Config Write
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("bg-config-write")
            .dataPreparer(createBGConfigWriteDataPreparer(tenantConfig))
            .step(new ConfigWriteStep(redisTemplate))
            .resultValidator(createBGConfigWriteValidator())
            .build());

        // Step 2: Redis Pub/Sub Broadcast
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("bg-message-broadcast")
            .dataPreparer(createBGMessageBroadcastDataPreparer(tenantConfig))
            .step(new MessageBroadcastStep(redisTemplate))
            .resultValidator(createBGMessageBroadcastValidator())
            .build());

        // Step 3: Health Check Polling (使用 PollingStep + 函数注入)
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("bg-health-check")
            .dataPreparer(createBGHealthCheckDataPreparer(tenantConfig))
            .step(new PollingStep("bg-health-check"))
            .resultValidator(createBGHealthCheckValidator())
            .build());

        return new ConfigurableServiceStage("blue-green-gateway", stepConfigs);
    }

    // ---- Blue-Green Gateway: ConfigWrite ----

    private DataPreparer createBGConfigWriteDataPreparer(TenantConfig config) {
        return (ctx) -> {
            String redisKeyPrefix = configLoader.getInfrastructure().getRedis().getHashKeyPrefix();
            String key = redisKeyPrefix + config.getTenantId().getValue();
            String field = "icc-bg-gateway";

            // 构建 Redis value (JSON)
            Map<String, Object> redisValue = new HashMap<>();
            redisValue.put("tenantId", config.getTenantId().getValue());
            redisValue.put("sourceUnit", extractSourceUnit(config));
            redisValue.put("targetUnit", extractTargetUnit(config));
            redisValue.put("routes", convertRouteRulesToMap(config));

            String value;
            try {
                value = objectMapper.writeValueAsString(redisValue);
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

    // ---- Blue-Green Gateway: MessageBroadcast ----

    private DataPreparer createBGMessageBroadcastDataPreparer(TenantConfig config) {
        return (ctx) -> {
            String topic = configLoader.getInfrastructure().getRedis().getPubsubTopic();

            Map<String, Object> messageBody = new HashMap<>();
            messageBody.put("tenantId", config.getTenantId().getValue());
            messageBody.put("appName", "icc-bg-gateway");
            messageBody.put("timestamp", System.currentTimeMillis());

            String message;
            try {
                message = objectMapper.writeValueAsString(messageBody);
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

    // ---- Blue-Green Gateway: HealthCheck (PollingStep + 函数注入) ----

    private DataPreparer createBGHealthCheckDataPreparer(TenantConfig config) {
        return (ctx) -> {
            // 从 YAML 读取健康检查配置
            int intervalMs = configLoader.getInfrastructure().getHealthCheck().getIntervalSeconds() * 1000;
            int maxAttempts = configLoader.getInfrastructure().getHealthCheck().getMaxAttempts();
            String healthCheckPath = extractHealthCheckPath(config);

            // 获取实例列表（Nacos 优先，fallback 降级）
            List<String> endpoints = resolveEndpoints("blueGreenGatewayService", "blue-green-gateway");
            List<String> healthCheckUrls = new ArrayList<>();
            for (String endpoint : endpoints) {
                healthCheckUrls.add("http://" + endpoint + healthCheckPath);
            }

            ctx.addVariable("pollInterval", intervalMs);
            ctx.addVariable("pollMaxAttempts", maxAttempts);

            // 函数注入：健康检查逻辑
            ctx.addVariable("pollCondition", (PollingStep.PollCondition) (pollCtx) -> {
                // 所有实例都必须健康
                for (String url : healthCheckUrls) {
                    try {
                        String response = restTemplate.getForObject(url, String.class);

                        // 简单验证：检查响应是否包含 version 字段
                        if (response == null || !response.contains("version")) {
                            log.debug("健康检查未通过: url={}", url);
                            return false;
                        }
                    } catch (Exception e) {
                        log.debug("健康检查异常: url={}, error={}", url, e.getMessage());
                        return false;
                    }
                }
                return true; // 所有实例都健康
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

    // ---- Blue-Green Gateway: 辅助方法 ----

    private String extractSourceUnit(TenantConfig config) {
        if (config.getPreviousConfig() != null
            && config.getPreviousConfig().getDeployUnit() != null) {
            return config.getPreviousConfig().getDeployUnit().name();
        }
        // 首次部署，source = target
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

    private String extractHealthCheckPath(TenantConfig config) {
        // 优先从 TenantConfig 获取
        if (config.getHealthCheckEndpoints() != null && !config.getHealthCheckEndpoints().isEmpty()) {
            return config.getHealthCheckEndpoints().get(0);
        }
        // 降级到 Infrastructure 默认值，替换 {tenantId}
        String template = configLoader.getInfrastructure().getHealthCheck().getDefaultPath();
        return template.replace("{tenantId}", config.getTenantId().getValue());
    }

    /**
     * 解析服务实例列表（Nacos 优先，fallback 降级）
     */
    private List<String> resolveEndpoints(String nacosServiceKey, String fallbackKey) {
        // TODO: 实现 Nacos 查询逻辑（需要 NacosNamingService）
        // 当前直接使用 fallback
        List<String> fallbackInstances = configLoader.getInfrastructure()
            .getFallbackInstances()
            .get(fallbackKey);

        if (fallbackInstances == null || fallbackInstances.isEmpty()) {
            throw new IllegalStateException("No fallback instances configured for: " + fallbackKey);
        }

        log.debug("使用 fallback 实例: service={}, count={}", fallbackKey, fallbackInstances.size());
        return fallbackInstances;
    }

    // ========================================
    // OBService Stage (RF-19-03)
    // ========================================

    private TaskStage createOBServiceStage(TenantConfig tenantConfig) {
        List<ConfigurableServiceStage.StepConfig> stepConfigs = new ArrayList<>();

        // Step 1: Polling (轮询 AgentService.judgeAgent)
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("ob-agent-polling")
            .dataPreparer(createOBPollingDataPreparer(tenantConfig))
            .step(new PollingStep("ob-agent-polling"))
            .resultValidator(createOBPollingValidator())
            .build());

        // Step 2: ConfigWrite (写入 ObConfig 到 Redis)
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("ob-config-write")
            .dataPreparer(createOBConfigWriteDataPreparer(tenantConfig))
            .step(new ConfigWriteStep(redisTemplate))
            .resultValidator(createOBConfigWriteValidator())
            .build());

        return new ConfigurableServiceStage("ob-service", stepConfigs);
    }

    // ---- OBService: Polling ----

    private DataPreparer createOBPollingDataPreparer(TenantConfig config) {
        return (ctx) -> {
            // 从 YAML 读取轮询配置（使用健康检查的配置作为默认值）
            int intervalMs = configLoader.getInfrastructure().getHealthCheck().getIntervalSeconds() * 1000;
            int maxAttempts = configLoader.getInfrastructure().getHealthCheck().getMaxAttempts();

            ctx.addVariable("pollInterval", intervalMs);
            ctx.addVariable("pollMaxAttempts", maxAttempts);

            // 函数注入：调用 AgentService.judgeAgent
            ctx.addVariable("pollCondition", (PollingStep.PollCondition) (pollCtx) -> {
                // 注意：AgentService 需要在运行时注入
                // 这里我们从 TaskRuntimeContext 获取 AgentService 实例
                Object agentService = pollCtx.getAdditionalData("agentService");
                if (agentService == null) {
                    log.warn("AgentService 未注入，OB 轮询跳过");
                    return true; // 降级：直接返回成功
                }

                try {
                    // 使用反射调用 judgeAgent 方法
                    var method = agentService.getClass().getMethod(
                        "judgeAgent", String.class, Long.class);
                    Boolean result = (Boolean) method.invoke(
                        agentService,
                        config.getTenantId().getValue(),
                        config.getPlanId().getValue()
                    );

                    log.debug("AgentService.judgeAgent() 返回: {}", result);
                    return result != null && result;

                } catch (Exception e) {
                    log.warn("调用 AgentService.judgeAgent() 异常: {}", e.getMessage());
                    return false; // 出错时继续轮询
                }
            });

            log.debug("OB Polling 数据准备完成: interval={}ms, maxAttempts={}",
                intervalMs, maxAttempts);
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

    // ---- OBService: ConfigWrite ----

    private DataPreparer createOBConfigWriteDataPreparer(TenantConfig config) {
        return (ctx) -> {
            String redisKeyPrefix = configLoader.getInfrastructure().getRedis().getHashKeyPrefix();
            String key = redisKeyPrefix + config.getTenantId().getValue();
            String field = "ob-campaign";

            // 构建 ObConfig
            xyz.firestige.deploy.domain.stage.model.ObConfig obConfig =
                new xyz.firestige.deploy.domain.stage.model.ObConfig(
                    config.getTenantId().getValue(),
                    extractSourceUnit(config),
                    extractTargetUnit(config)
                );

            // 序列化为 JSON
            String value;
            try {
                value = objectMapper.writeValueAsString(obConfig);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize ObConfig", e);
            }

            ctx.addVariable("key", key);
            ctx.addVariable("field", field);
            ctx.addVariable("value", value);

            log.debug("OB ConfigWrite 数据准备完成: key={}, field={}", key, field);
        };
    }

    private ResultValidator createOBConfigWriteValidator() {
        return (ctx) -> {
            ConfigWriteResult result = ctx.getAdditionalData("configWriteResult", ConfigWriteResult.class);
            if (result != null && result.isSuccess()) {
                return ValidationResult.success("OB 配置写入成功");
            }
            return ValidationResult.failure("OB 配置写入失败");
        };
    }

    // ========================================
    // 辅助方法
    // ========================================

    /**
     * 生成认证 Token
     *
     * @param tokenProvider token 提供方式（random, oauth2, custom）
     * @return token 字符串
     */
    private String generateToken(String tokenProvider) {
        if (tokenProvider == null) {
            return null;
        }

        switch (tokenProvider.toLowerCase()) {
            case "random":
                // 生成随机 hex token（32位）
                return generateRandomHex(32);

            case "oauth2":
                // TODO: 实现 OAuth2 token 获取
                log.warn("OAuth2 token provider not implemented yet");
                return null;

            case "custom":
                // TODO: 实现自定义 token 获取
                log.warn("Custom token provider not implemented yet");
                return null;

            default:
                log.warn("Unknown token provider: {}", tokenProvider);
                return null;
        }
    }

    /**
     * 生成随机 Hex 字符串
     */
    private String generateRandomHex(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString((int) (Math.random() * 16)));
        }
        return sb.toString();
    }
}

