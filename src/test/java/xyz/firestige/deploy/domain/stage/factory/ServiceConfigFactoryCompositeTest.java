package xyz.firestige.deploy.domain.stage.factory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.firestige.deploy.application.dto.DeployUnitIdentifier;
import xyz.firestige.deploy.application.dto.MediaRoutingConfig;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.vo.RouteRule;
import xyz.firestige.deploy.domain.stage.config.ASBCGatewayConfig;
import xyz.firestige.deploy.domain.stage.config.BlueGreenGatewayConfig;
import xyz.firestige.deploy.domain.stage.config.PortalConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.template.TemplateResolver;
import xyz.firestige.entity.deploy.NetworkEndpoint;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 服务配置工厂组合器测试
 */
class ServiceConfigFactoryCompositeTest {
    
    private ServiceConfigFactoryComposite factoryComposite;
    private TenantConfig baseTenantConfig;
    
    @BeforeEach
    void setUp() {
        // 创建 mock 依赖
        DeploymentConfigLoader configLoader = mock(DeploymentConfigLoader.class);
        TemplateResolver templateResolver = new TemplateResolver();

        // 创建工厂实例
        List<ServiceConfigFactory> factories = List.of(
                new BlueGreenGatewayConfigFactory(configLoader, templateResolver),
                new PortalConfigFactory(configLoader, templateResolver),
                new ASBCGatewayConfigFactory()
        );
        factoryComposite = new ServiceConfigFactoryComposite(factories);
        
        // 准备基础 TenantConfig
        baseTenantConfig = createBaseTenantConfig();
    }
    
    @Test
    void testCreateBlueGreenGatewayConfig() {
        // When
        ServiceConfig config = factoryComposite.createConfig("blue-green-gateway", baseTenantConfig);
        
        // Then
        assertNotNull(config);
        assertTrue(config instanceof BlueGreenGatewayConfig);
        
        BlueGreenGatewayConfig bgConfig = (BlueGreenGatewayConfig) config;
        assertEquals("tenant-001", bgConfig.getTenantId());
        assertEquals(1L, bgConfig.getConfigVersion());
        assertEquals("test-namespace", bgConfig.getNacosNamespace());
        assertEquals("/actuator/health", bgConfig.getHealthCheckPath());
        assertEquals(2, bgConfig.getRoutingData().size());
        assertEquals("value1", bgConfig.getRoutingData().get("key1"));
        
        // 验证 Redis 相关方法
        assertEquals("deploy:config:tenant-001", bgConfig.getRedisHashKey());
        assertEquals("blue-green-gateway", bgConfig.getRedisHashField());
        assertEquals("deploy.config.notify", bgConfig.getRedisPubSubTopic());
        assertEquals("blue-green-gateway", bgConfig.getRedisPubSubMessage());
    }
    
    @Test
    void testCreatePortalConfig() {
        // When
        ServiceConfig config = factoryComposite.createConfig("portal", baseTenantConfig);
        
        // Then
        assertNotNull(config);
        assertTrue(config instanceof PortalConfig);
        
        PortalConfig portalConfig = (PortalConfig) config;
        assertEquals("tenant-001", portalConfig.getTenantId());
        assertEquals(1L, portalConfig.getConfigVersion());
        assertEquals("test-namespace", portalConfig.getNacosNamespace());
        
        // 验证 Redis 相关方法
        assertEquals("deploy:config:tenant-001", portalConfig.getRedisHashKey());
        assertEquals("portal", portalConfig.getRedisHashField());
        assertEquals("deploy.config.notify", portalConfig.getRedisPubSubTopic());
        assertEquals("portal", portalConfig.getRedisPubSubMessage());
    }
    
    @Test
    void testCreateASBCGatewayConfig() {
        // Given - 添加媒体路由配置
        baseTenantConfig.setMediaRoutingConfig(
                new MediaRoutingConfig("trunk-group-001", "rules-001")
        );
        
        // When
        ServiceConfig config = factoryComposite.createConfig("asbc-gateway", baseTenantConfig);
        
        // Then
        assertNotNull(config);
        assertTrue(config instanceof ASBCGatewayConfig);
        
        ASBCGatewayConfig asbcConfig = (ASBCGatewayConfig) config;
        assertEquals("tenant-001", asbcConfig.getTenantId());
        assertEquals(1L, asbcConfig.getConfigVersion());
        assertNotNull(asbcConfig.getFixedInstances());
        assertFalse(asbcConfig.getFixedInstances().isEmpty());
        assertEquals("/api/v1/config", asbcConfig.getConfigEndpoint());
        
        // 验证媒体路由转换
        ASBCGatewayConfig.MediaRouting mediaRouting = asbcConfig.getMediaRouting();
        assertNotNull(mediaRouting);
        assertEquals("trunk-group-001", mediaRouting.trunkGroup());
        assertEquals("rules-001", mediaRouting.calledNumberRules());
    }
    
    @Test
    void testUnsupportedServiceType() {
        // When & Then
        assertThrows(UnsupportedOperationException.class, 
                () -> factoryComposite.createConfig("unknown-service", baseTenantConfig));
    }
    
    @Test
    void testSupports() {
        assertTrue(factoryComposite.supports("blue-green-gateway"));
        assertTrue(factoryComposite.supports("portal"));
        assertTrue(factoryComposite.supports("asbc-gateway"));
        assertFalse(factoryComposite.supports("unknown-service"));
    }
    
    @Test
    void testGetSupportedServiceTypes() {
        List<String> supportedTypes = factoryComposite.getSupportedServiceTypes();
        assertEquals(3, supportedTypes.size());
        assertTrue(supportedTypes.contains("blue-green-gateway"));
        assertTrue(supportedTypes.contains("portal"));
        assertTrue(supportedTypes.contains("asbc-gateway"));
    }
    
    @Test
    void testNullServiceType() {
        assertThrows(IllegalArgumentException.class,
                () -> factoryComposite.createConfig(null, baseTenantConfig));
    }
    
    @Test
    void testNullTenantConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> factoryComposite.createConfig("blue-green-gateway", null));
    }
    
    @Test
    void testMissingNetworkEndpoints() {
        // Given
        baseTenantConfig.setRouteRules(null);
        
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> factoryComposite.createConfig("blue-green-gateway", baseTenantConfig));
    }
    
    @Test
    void testMissingMediaRoutingForASBC() {
        // Given - 没有设置 MediaRoutingConfig
        
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> factoryComposite.createConfig("asbc-gateway", baseTenantConfig));
    }
    
    // ========== 辅助方法 ==========
    
    private TenantConfig createBaseTenantConfig() {
        TenantConfig config = new TenantConfig();
        config.setTenantId("tenant-001");
        config.setDeployUnit(new DeployUnitIdentifier(100L, 1L, "test-unit"));
        config.setNacosNameSpace("test-namespace");

        List<RouteRule> routeRules = List.of(RouteRule.of("k", "http://source", "http://target", "http://health", "http://hc"));
        config.setRouteRules(routeRules);
        
        return config;
    }
}
