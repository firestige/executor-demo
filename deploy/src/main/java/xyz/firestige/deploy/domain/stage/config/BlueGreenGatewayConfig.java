package xyz.firestige.deploy.domain.stage.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.stage.model.BlueGreenGatewayRedisValue;

import java.util.Map;

/**
 * 蓝绿网关配置（领域模型）
 * 
 * 用于 blue-green-gateway 服务的部署配置
 * 包含 Redis 写入、消息广播和健康检查所需的所有数据
 */
public class BlueGreenGatewayConfig implements ServiceConfig {
    
    private final TenantId tenantId;
    private final Long configVersion;
    private final String nacosNamespace;
    private final String nacosServiceName;
    private final String healthCheckPath;
    private final Map<String, String> routingData;  // key-value 路由数据（已弃用，保留兼容性）
    private final BlueGreenGatewayRedisValue redisValue;  // Redis 写入的实际值对象

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public BlueGreenGatewayConfig(
            TenantId tenantId,
            Long configVersion,
            String nacosNamespace,
            String nacosServiceName,
            String healthCheckPath,
            Map<String, String> routingData,
            BlueGreenGatewayRedisValue redisValue) {

        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId cannot be null or blank");
        }
        if (configVersion == null) {
            throw new IllegalArgumentException("configVersion cannot be null");
        }
        if (nacosNamespace == null) {
            throw new IllegalArgumentException("nacosNamespace cannot be null");
        }
        if (nacosServiceName == null) {
            throw new IllegalArgumentException("nacosServiceName cannot be null");
        }
        if (healthCheckPath == null) {
            throw new IllegalArgumentException("healthCheckPath cannot be null");
        }
        if (redisValue == null) {
            throw new IllegalArgumentException("redisValue cannot be null");
        }
        
        this.tenantId = tenantId;
        this.configVersion = configVersion;
        this.nacosNamespace = nacosNamespace;
        this.nacosServiceName = nacosServiceName;
        this.healthCheckPath = healthCheckPath;
        this.routingData = routingData != null ? Map.copyOf(routingData) : Map.of();
        this.redisValue = redisValue;
    }
    
    @Override
    public String getServiceType() {
        return "blue-green-gateway";
    }
    
    @Override
    public TenantId getTenantId() {
        return tenantId;
    }
    
    public Long getConfigVersion() {
        return configVersion;
    }
    
    public String getNacosNamespace() {
        return nacosNamespace;
    }
    
    public String getNacosServiceName() {
        return nacosServiceName;
    }
    
    public String getHealthCheckPath() {
        return healthCheckPath;
    }
    
    public Map<String, String> getRoutingData() {
        return routingData;
    }
    
    /**
     * 获取 Redis value 对象
     */
    public BlueGreenGatewayRedisValue getRedisValue() {
        return redisValue;
    }

    /**
     * 获取 Redis value 的 JSON 字符串
     * 用于写入 Redis Hash
     */
    public String getRedisValueJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(redisValue);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize redisValue to JSON", e);
        }
    }

    /**
     * 构建 Redis Hash 的 key
     * 格式: icc_ai_ops_srv:tenant_config:{tenantId}
     */
    public String getRedisHashKey() {
        return "icc_ai_ops_srv:tenant_config:" + tenantId;
    }
    
    /**
     * 构建 Redis Hash 的 field
     * 蓝绿网关使用 "icc-bg-gateway" 作为 field 名
     */
    public String getRedisHashField() {
        return "icc-bg-gateway";
    }
    
    /**
     * 构建 Redis Pub/Sub 的 topic
     * 固定为 "deploy.config.notify"
     */
    public String getRedisPubSubTopic() {
        return "deploy.config.notify";
    }
    
    /**
     * 构建 Redis Pub/Sub 的 message
     * 只包含 serviceName
     */
    public String getRedisPubSubMessage() {
        return "blue-green-gateway";
    }
    
    @Override
    public String toString() {
        return "BlueGreenGatewayConfig{" +
                "tenantId='" + tenantId + '\'' +
                ", configVersion=" + configVersion +
                ", nacosServiceName='" + nacosServiceName + '\'' +
                ", routingDataSize=" + routingData.size() +
                '}';
    }
}
