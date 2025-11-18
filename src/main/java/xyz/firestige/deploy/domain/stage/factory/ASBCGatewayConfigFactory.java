package xyz.firestige.deploy.domain.stage.factory;

import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.MediaRoutingConfig;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.stage.config.ASBCGatewayConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;

import java.util.Arrays;
import java.util.List;

/**
 * ASBC 网关配置工厂
 * 
 * 转换逻辑：
 * 1. 从 TenantConfig.mediaRoutingConfig 提取 ASBC 专用的媒体路由配置
 * 2. 从配置文件读取固定实例列表（TODO: 配置化）
 * 3. 使用固定的配置接口路径
 * 
 * 注意：ASBC 网关不使用 Nacos 服务发现，实例列表是固定的
 */
@Component
public class ASBCGatewayConfigFactory implements ServiceConfigFactory {
    
    private static final String SERVICE_TYPE = "asbc-gateway";
    private static final String CONFIG_ENDPOINT = "/api/v1/config";  // TODO: 从配置读取
    // TODO: 从配置文件读取固定实例列表
    private static final List<String> FIXED_INSTANCES = Arrays.asList(
            "192.168.1.100:8080",
            "192.168.1.101:8080"
    );
    
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
        
        // 3. 提取并转换媒体路由配置
        MediaRoutingConfig mediaRoutingConfig = tenantConfig.getMediaRoutingConfig();
        if (mediaRoutingConfig == null || !mediaRoutingConfig.isEnabled()) {
            throw new IllegalArgumentException("mediaRoutingConfig must be enabled for ASBC gateway");
        }
        
        ASBCGatewayConfig.MediaRouting mediaRouting = new ASBCGatewayConfig.MediaRouting(
                mediaRoutingConfig.trunkGroup(),
                mediaRoutingConfig.calledNumberRules()
        );
        
        // 4. 创建领域配置对象
        return new ASBCGatewayConfig(
                tenantId,
                configVersion,
                FIXED_INSTANCES,
                CONFIG_ENDPOINT,
                mediaRouting
        );
    }
}
