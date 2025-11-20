package xyz.firestige.deploy.domain.stage.factory;

import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.stage.config.PortalConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.template.TemplateResolver;

import java.util.List;
import java.util.Map;

/**
 * Portal 配置工厂
 * 
 * 转换逻辑与蓝绿网关相同，只是服务标识不同
 * 注意：Portal 暂时使用简化的配置格式，未来可能需要扩展
 */
@Component
public class PortalConfigFactory implements ServiceConfigFactory {
    
    private static final String SERVICE_TYPE = "portal";
    private static final String NACOS_SERVICE_NAME = "portal-service";  // TODO: 从配置读取

    private final DeploymentConfigLoader configLoader;
    private final TemplateResolver templateResolver;

    public PortalConfigFactory(
            DeploymentConfigLoader configLoader,
            TemplateResolver templateResolver) {
        this.configLoader = configLoader;
        this.templateResolver = templateResolver;
    }

    @Override
    public boolean supports(String serviceType) {
        return SERVICE_TYPE.equals(serviceType);
    }
    
    @Override
    public ServiceConfig create(TenantConfig tenantConfig) {
        if (tenantConfig == null) {
            throw new IllegalArgumentException("tenantConfig cannot be null");
        }
        
        TenantId tenantId = tenantConfig.getTenantId();
        if (tenantId == null) {
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

        // Portal 暂时使用空 Map，未来可以扩展为类似 BlueGreenGateway 的结构
        return new PortalConfig(
                tenantId,
                configVersion,
                nacosNamespace,
                NACOS_SERVICE_NAME,
                healthCheckPath,
                Map.of()  // 空 Map
        );
    }
    
    private String extractHealthCheckPath(TenantConfig tenantConfig) {
        String pathTemplate;

        // 1. 优先从 TenantConfig 获取
        List<String> healthCheckEndpoints = tenantConfig.getHealthCheckEndpoints();
        if (healthCheckEndpoints != null && !healthCheckEndpoints.isEmpty()) {
            pathTemplate = healthCheckEndpoints.get(0);
        } else {
            // 2. 从 Infrastructure 配置获取默认模板
            pathTemplate = configLoader.getInfrastructure()
                    .getHealthCheck()
                    .getDefaultPath();
        }

        // 3. 替换模板变量 {tenantId}
        Map<String, String> variables = Map.of(
            "tenantId", tenantConfig.getTenantId().getValue()
        );

        return (String) templateResolver.resolve(pathTemplate, variables);
    }
}
