package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.stage.config.BlueGreenGatewayConfig;
import xyz.firestige.deploy.domain.stage.config.PortalConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.execution.stage.AbstractConfigurableStep;

import java.util.Map;
import java.util.Objects;

/**
 * Redis Hash 写入步骤（可复用）
 * 
 * 配置来源：
 * - YAML: hash-field（固定字段名）
 * - Infrastructure: hash-key-prefix（固定前缀）
 * - ServiceConfig: tenantId, configVersion, routingData（运行时数据）
 * 
 * 用于：blue-green-gateway, portal
 */
public class KeyValueWriteStep extends AbstractConfigurableStep {
    
    private static final Logger log = LoggerFactory.getLogger(KeyValueWriteStep.class);
    
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DeploymentConfigLoader configLoader;
    
    public KeyValueWriteStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            DeploymentConfigLoader configLoader) {
        
        super(stepName, stepConfig, serviceConfig);
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader cannot be null");
    }
    
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 1. 从 YAML 配置获取 Hash field（与 YAML 配置保持一致使用驼峰命名）
        String hashField = getConfigValue("hashField", null);
        if (hashField == null || hashField.isBlank()) {
            throw new IllegalArgumentException("hashField not configured in YAML");
        }
        
        // 2. 从 ServiceConfig 获取运行时数据
        TenantId tenantId = serviceConfig.getTenantId();
        
        // 3. 从 Infrastructure 配置获取 key 前缀
        String keyPrefix = configLoader.getInfrastructure()
                .getRedis()
                .getHashKeyPrefix();
        String hashKey = keyPrefix + tenantId.getValue();
        
        // 4. 获取 JSON 字符串（使用新的 Redis value 对象）
        String jsonValue = getRedisValueJson();

        // 5. 第一次写入：业务数据
        redisTemplate.opsForHash().put(hashKey, hashField, jsonValue);
        
        log.info("[KeyValueWriteStep] Redis Hash written: key={}, field={}, valueLength={}",
                hashKey, hashField, jsonValue.length());
        log.debug("[KeyValueWriteStep] JSON value: {}", jsonValue);

        // 6. 第二次写入：metadata（包含 planVersion）
        Long planVersion = ctx.getAdditionalData("planVersion", Long.class);
        if (planVersion != null) {
            String metadataJson = objectMapper.writeValueAsString(
                Map.of("version", planVersion)
            );
            redisTemplate.opsForHash().put(hashKey, "metadata", metadataJson);
            log.info("[KeyValueWriteStep] Redis Hash metadata written: key={}, field=metadata, version={}",
                    hashKey, planVersion);
        } else {
            log.warn("[KeyValueWriteStep] planVersion not found in context, skipping metadata write");
        }
    }
    
    /**
     * 根据 ServiceConfig 类型获取 Redis value JSON
     */
    private String getRedisValueJson() {
        if (serviceConfig instanceof BlueGreenGatewayConfig bgConfig) {
            return bgConfig.getRedisValueJson();
        } else if (serviceConfig instanceof PortalConfig portalConfig) {
            // Portal 暂时使用旧的格式（后续可以扩展）
            Map<String, Object> data = Map.of(
                "version", portalConfig.getConfigVersion(),
                "routing", portalConfig.getRoutingData()
            );
            try {
                return objectMapper.writeValueAsString(data);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize portal config", e);
            }
        } else {
            throw new UnsupportedOperationException(
                    "KeyValueWriteStep does not support: " + serviceConfig.getClass().getName());
        }
    }
}
