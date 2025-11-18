package xyz.firestige.deploy.domain.stage.config;

import java.util.List;
import java.util.Map;

/**
 * 蓝绿网关配置（领域模型）
 * 
 * 用于 blue-green-gateway 服务的部署配置
 * 包含 Redis 写入、消息广播和健康检查所需的所有数据
 */
public class BlueGreenGatewayConfig implements ServiceConfig {
    
    private final String tenantId;
    private final Long configVersion;
    private final String nacosNamespace;
    private final String nacosServiceName;
    private final String healthCheckPath;
    private final Map<String, String> routingData;  // key-value 路由数据
    
    public BlueGreenGatewayConfig(
            String tenantId,
            Long configVersion,
            String nacosNamespace,
            String nacosServiceName,
            String healthCheckPath,
            Map<String, String> routingData) {
        
        if (tenantId == null || tenantId.isBlank()) {
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
        if (routingData == null || routingData.isEmpty()) {
            throw new IllegalArgumentException("routingData cannot be null or empty");
        }
        
        this.tenantId = tenantId;
        this.configVersion = configVersion;
        this.nacosNamespace = nacosNamespace;
        this.nacosServiceName = nacosServiceName;
        this.healthCheckPath = healthCheckPath;
        this.routingData = Map.copyOf(routingData);  // 不可变
    }
    
    @Override
    public String getServiceType() {
        return "blue-green-gateway";
    }
    
    @Override
    public String getTenantId() {
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
     * 构建 Redis Hash 的 key
     * 格式: deploy:config:{tenantId}
     */
    public String getRedisHashKey() {
        return "deploy:config:" + tenantId;
    }
    
    /**
     * 构建 Redis Hash 的 field
     * 蓝绿网关使用 "blue-green-gateway" 作为 field 名
     */
    public String getRedisHashField() {
        return "blue-green-gateway";
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
