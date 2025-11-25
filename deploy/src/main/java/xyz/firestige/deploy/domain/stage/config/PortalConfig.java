package xyz.firestige.deploy.domain.stage.config;

import xyz.firestige.deploy.domain.shared.vo.TenantId;

import java.util.Map;

/**
 * Portal 配置（领域模型）
 * 
 * 用于 portal 服务的部署配置
 * 与蓝绿网关配置结构相同，但服务标识不同
 */
public class PortalConfig implements ServiceConfig {
    
    private final TenantId tenantId;
    private final Long configVersion;
    private final String nacosNamespace;
    private final String nacosServiceName;
    private final String healthCheckPath;
    private final Map<String, String> routingData;
    
    public PortalConfig(
            TenantId tenantId,
            Long configVersion,
            String nacosNamespace,
            String nacosServiceName,
            String healthCheckPath,
            Map<String, String> routingData) {
        
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
        if (routingData == null || routingData.isEmpty()) {
//            throw new IllegalArgumentException("routingData cannot be null or empty");
        }
        
        this.tenantId = tenantId;
        this.configVersion = configVersion;
        this.nacosNamespace = nacosNamespace;
        this.nacosServiceName = nacosServiceName;
        this.healthCheckPath = healthCheckPath;
        this.routingData = Map.copyOf(routingData);
    }
    
    @Override
    public String getServiceType() {
        return "portal";
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
    
    public String getRedisHashKey() {
        return "deploy:config:" + tenantId;
    }
    
    public String getRedisHashField() {
        return "portal";
    }
    
    public String getRedisPubSubTopic() {
        return "deploy.config.notify";
    }
    
    public String getRedisPubSubMessage() {
        return "portal";
    }
    
    @Override
    public String toString() {
        return "PortalConfig{" +
                "tenantId='" + tenantId + '\'' +
                ", configVersion=" + configVersion +
                ", nacosServiceName='" + nacosServiceName + '\'' +
                ", routingDataSize=" + routingData.size() +
                '}';
    }
}
