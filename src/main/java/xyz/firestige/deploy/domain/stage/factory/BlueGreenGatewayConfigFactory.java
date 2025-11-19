package xyz.firestige.deploy.domain.stage.factory;

import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.stage.config.BlueGreenGatewayConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;
import xyz.firestige.deploy.domain.stage.model.BlueGreenGatewayRedisValue;
import xyz.firestige.deploy.domain.stage.model.RouteInfo;
import xyz.firestige.deploy.domain.shared.vo.RouteRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 蓝绿网关配置工厂
 * 
 * 转换逻辑：
 * 1. 从 TenantConfig.routeRules 提取路由信息
 * 2. 从 TenantConfig.deployUnit 提取当前和上一次的部署单元名称
 * 3. 构建 BlueGreenGatewayRedisValue 对象
 * 4. 从 TenantConfig.nacosNameSpace 和固定 serviceName 构建服务发现信息
 * 5. 使用默认或配置的健康检查路径
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
        TenantId tenantId = tenantConfig.getTenantId();
        if (tenantId == null) {
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
        
        // 5. 构建 Redis value 对象
        BlueGreenGatewayRedisValue redisValue = buildRedisValue(tenantConfig);

        // 6. 创建领域配置对象（routingData 设置为空 Map，已弃用）
        return new BlueGreenGatewayConfig(
                tenantId,
                configVersion,
                nacosNamespace,
                NACOS_SERVICE_NAME,
                healthCheckPath,
                Map.of(),  // 空 Map，routingData 已弃用
                redisValue
        );
    }

    /**
     * 构建 Redis value 对象
     */
    private BlueGreenGatewayRedisValue buildRedisValue(TenantConfig tenantConfig) {
        // 1. 租户 ID
        TenantId tenantId = tenantConfig.getTenantId();

        // 2. 目标部署单元名称（当前配置）
        String targetUnitName = null;
        if (tenantConfig.getDeployUnit() != null) {
            targetUnitName = tenantConfig.getDeployUnit().name();
        }
        if (targetUnitName == null || targetUnitName.isBlank()) {
            throw new IllegalArgumentException("deployUnit.name cannot be null or blank");
        }

        // 3. 来源部署单元名称（上一次配置）
        String sourceUnitName = null;
        if (tenantConfig.getPreviousConfig() != null
                && tenantConfig.getPreviousConfig().getDeployUnit() != null) {
            sourceUnitName = tenantConfig.getPreviousConfig().getDeployUnit().name();
        }
        // 如果没有上一次配置，sourceUnitName 可以为 null 或与 targetUnitName 相同
        if (sourceUnitName == null || sourceUnitName.isBlank()) {
            sourceUnitName = targetUnitName;  // 首次部署，source = target
        }

        // 4. 转换路由规则
        List<RouteInfo> routes = convertRouteRules(tenantConfig.getRouteRules());

        return new BlueGreenGatewayRedisValue(tenantId, sourceUnitName, targetUnitName, routes);
    }

    /**
     * 转换路由规则为 RouteInfo 列表
     */
    private List<RouteInfo> convertRouteRules(List<RouteRule> routeRules) {
        if (routeRules == null || routeRules.isEmpty()) {
            throw new IllegalArgumentException("routeRules cannot be null or empty");
        }

        List<RouteInfo> routes = new ArrayList<>();
        for (RouteRule rule : routeRules) {
            RouteInfo routeInfo = new RouteInfo(
                    rule.id(),
                    rule.sourceUri().toString(),
                    rule.targetUri().toString()
            );
            routes.add(routeInfo);
        }

        return routes;
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
}
