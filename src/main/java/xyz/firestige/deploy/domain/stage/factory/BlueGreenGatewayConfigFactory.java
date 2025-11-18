package xyz.firestige.deploy.domain.stage.factory;

import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.stage.config.BlueGreenGatewayConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;
import xyz.firestige.entity.deploy.NetworkEndpoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 蓝绿网关配置工厂
 * 
 * 转换逻辑：
 * 1. 从 TenantConfig.networkEndpoints 提取 key-value 路由数据
 * 2. 从 TenantConfig.deployUnit.version 映射为 configVersion
 * 3. 从 TenantConfig.nacosNameSpace 和固定 serviceName 构建服务发现信息
 * 4. 使用默认或配置的健康检查路径
 */
@Component
public class BlueGreenGatewayConfigFactory implements ServiceConfigFactory {
    
    private static final String SERVICE_TYPE = "blue-green-gateway";
    private static final String NACOS_SERVICE_NAME = "blue-green-gateway-service";  // TODO: 从配置读取
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
        
        // 1. 提取租户标识
        String tenantId = tenantConfig.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be null or blank");
        }
        
        // 2. 提取配置版本
        Long configVersion = tenantConfig.getDeployUnitVersion();
        if (configVersion == null) {
            throw new IllegalArgumentException("deployUnit.version cannot be null");
        }
        
        // 3. 提取 Nacos 命名空间
        String nacosNamespace = tenantConfig.getNacosNameSpace();
        if (nacosNamespace == null) {
            throw new IllegalArgumentException("nacosNameSpace cannot be null");
        }
        
        // 4. 提取健康检查路径
        String healthCheckPath = extractHealthCheckPath(tenantConfig);
        
        // 5. 转换 NetworkEndpoint 列表为 Map<String, String>
        Map<String, String> routingData = convertNetworkEndpoints(tenantConfig.getNetworkEndpoints());
        
        // 6. 创建领域配置对象
        return new BlueGreenGatewayConfig(
                tenantId,
                configVersion,
                nacosNamespace,
                NACOS_SERVICE_NAME,
                healthCheckPath,
                routingData
        );
    }
    
    /**
     * 提取健康检查路径
     * 优先级：TenantConfig.healthCheckEndpoints > 默认值
     */
    private String extractHealthCheckPath(TenantConfig tenantConfig) {
        List<String> healthCheckEndpoints = tenantConfig.getHealthCheckEndpoints();
        if (healthCheckEndpoints != null && !healthCheckEndpoints.isEmpty()) {
            return healthCheckEndpoints.get(0);  // 使用第一个
        }
        return DEFAULT_HEALTH_CHECK_PATH;
    }
    
    /**
     * 转换 NetworkEndpoint 列表为 key-value Map
     * 
     * 转换规则：
     * - 使用 NetworkEndpoint.key 作为 Map key
     * - 使用 NetworkEndpoint.value 作为 Map value
     * - 跳过 key 或 value 为空的条目
     */
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
