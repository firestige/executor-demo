package xyz.firestige.deploy.domain.stage.factory;

import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.stage.config.PortalConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;
import xyz.firestige.entity.deploy.NetworkEndpoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal 配置工厂
 * 
 * 转换逻辑与蓝绿网关相同，只是服务标识不同
 */
@Component
public class PortalConfigFactory implements ServiceConfigFactory {
    
    private static final String SERVICE_TYPE = "portal";
    private static final String NACOS_SERVICE_NAME = "portal-service";  // TODO: 从配置读取
    private static final String DEFAULT_HEALTH_CHECK_PATH = "/actuator/health";
    
    @Override
    public boolean supports(String serviceType) {
        return SERVICE_TYPE.equals(serviceType);
    }
    
    @Override
    public ServiceConfig create(TenantConfig tenantConfig) {
        if (tenantConfig == null) {
            throw new IllegalArgumentException("tenantConfig cannot be null");
        }
        
        String tenantId = tenantConfig.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be null or blank");
        }
        
        Long configVersion = tenantConfig.getDeployUnitVersion();
        if (configVersion == null) {
            throw new IllegalArgumentException("deployUnit.version cannot be null");
        }
        
        String nacosNamespace = tenantConfig.getNacosNameSpace();
        if (nacosNamespace == null) {
            throw new IllegalArgumentException("nacosNameSpace cannot be null");
        }
        
        String healthCheckPath = extractHealthCheckPath(tenantConfig);
        Map<String, String> routingData = convertNetworkEndpoints(tenantConfig.getNetworkEndpoints());
        
        return new PortalConfig(
                tenantId,
                configVersion,
                nacosNamespace,
                NACOS_SERVICE_NAME,
                healthCheckPath,
                routingData
        );
    }
    
    private String extractHealthCheckPath(TenantConfig tenantConfig) {
        List<String> healthCheckEndpoints = tenantConfig.getHealthCheckEndpoints();
        if (healthCheckEndpoints != null && !healthCheckEndpoints.isEmpty()) {
            return healthCheckEndpoints.get(0);
        }
        return DEFAULT_HEALTH_CHECK_PATH;
    }
    
    private Map<String, String> convertNetworkEndpoints(List<NetworkEndpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("networkEndpoints cannot be null or empty");
        }
        
        Map<String, String> routingData = new HashMap<>();
        for (NetworkEndpoint endpoint : endpoints) {
            if (endpoint.getKey() != null && !endpoint.getKey().isBlank() 
                    && endpoint.getValue() != null && !endpoint.getValue().isBlank()) {
                routingData.put(endpoint.getKey(), endpoint.getValue());
            }
        }
        
        if (routingData.isEmpty()) {
            throw new IllegalArgumentException("No valid key-value pairs found in networkEndpoints");
        }
        
        return routingData;
    }
}
