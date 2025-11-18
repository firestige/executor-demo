package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
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
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final DeploymentConfigLoader configLoader;
    
    public KeyValueWriteStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            DeploymentConfigLoader configLoader) {
        
        super(stepName, stepConfig, serviceConfig);
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader cannot be null");
    }
    
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 1. 从 YAML 配置获取 Hash field
        String hashField = getConfigValue("hash-field", null);
        if (hashField == null || hashField.isBlank()) {
            throw new IllegalArgumentException("hash-field not configured in YAML");
        }
        
        // 2. 从 ServiceConfig 获取运行时数据
        String tenantId = serviceConfig.getTenantId();
        
        // 3. 从 Infrastructure 配置获取 key 前缀
        String keyPrefix = configLoader.getInfrastructure()
                .getRedis()
                .getHashKeyPrefix();
        String hashKey = keyPrefix + tenantId;
        
        // 4. 构建写入数据（类型安全）
        Map<String, Object> data = buildData();
        
        // 5. 序列化为 JSON 并写入 Redis
        String jsonValue = objectMapper.writeValueAsString(data);
        redisTemplate.opsForHash().put(hashKey, hashField, jsonValue);
        
        log.info("[KeyValueWriteStep] Redis Hash written: key={}, field={}, version={}", 
                hashKey, hashField, data.get("version"));
    }
    
    /**
     * 根据 ServiceConfig 类型构建数据
     */
    private Map<String, Object> buildData() {
        if (serviceConfig instanceof BlueGreenGatewayConfig bgConfig) {
            return Map.of(
                "version", bgConfig.getConfigVersion(),
                "routing", bgConfig.getRoutingData()
            );
        } else if (serviceConfig instanceof PortalConfig portalConfig) {
            return Map.of(
                "version", portalConfig.getConfigVersion(),
                "routing", portalConfig.getRoutingData()
            );
        } else {
            throw new UnsupportedOperationException(
                    "KeyValueWriteStep does not support: " + serviceConfig.getClass().getName());
        }
    }
}
