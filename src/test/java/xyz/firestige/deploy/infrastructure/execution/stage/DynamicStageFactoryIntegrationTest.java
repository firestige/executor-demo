package xyz.firestige.deploy.infrastructure.execution.stage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.application.dto.DeployUnitIdentifier;
import xyz.firestige.deploy.application.dto.MediaRoutingConfig;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.vo.RouteRule;
import xyz.firestige.deploy.domain.stage.factory.ASBCGatewayConfigFactory;
import xyz.firestige.deploy.domain.stage.factory.BlueGreenGatewayConfigFactory;
import xyz.firestige.deploy.domain.stage.factory.PortalConfigFactory;
import xyz.firestige.deploy.domain.stage.factory.ServiceConfigFactory;
import xyz.firestige.deploy.domain.stage.factory.ServiceConfigFactoryComposite;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.ASBCConfigRequestStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.EndpointPollingStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.KeyValueWriteStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.MessageBroadcastStep;
import xyz.firestige.entity.deploy.NetworkEndpoint;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DynamicStageFactory 集成测试
 * 验证配置驱动的 Stage 动态创建
 */
class DynamicStageFactoryIntegrationTest {
    
    private DynamicStageFactory stageFactory;
    private ServiceConfigFactoryComposite configFactoryComposite;
    private DeploymentConfigLoader configLoader;
    
    @BeforeEach
    void setUp() {
        // 1. 创建配置加载器
        configLoader = new DeploymentConfigLoader();
        configLoader.loadConfig();
        
        // 2. 创建服务配置工厂组合器
        List<ServiceConfigFactory> factories = List.of(
                new BlueGreenGatewayConfigFactory(),
                new PortalConfigFactory(),
                new ASBCGatewayConfigFactory()
        );
        configFactoryComposite = new ServiceConfigFactoryComposite(factories);
        
        // 3. 创建步骤注册表（使用实际对象，避免 Mockito/ByteBuddy 问题）
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        RestTemplate restTemplate = new RestTemplate();
        
        StepRegistry stepRegistry = new StepRegistry(
                redisTemplate,  // 真实 RedisTemplate（不会执行操作）
                restTemplate,   // 真实 RestTemplate
                configLoader,
                new ObjectMapper(),
                null   // nacosNamingService (optional)
        );
        
        // 4. 创建动态工厂
        stageFactory = new DynamicStageFactory(
                configFactoryComposite,
                configLoader,
                stepRegistry
        );
    }
    
    @Test
    void shouldCreateStagesForBlueGreenGateway() {
        // Given
        TenantConfig tenantConfig = createBlueGreenGatewayConfig();
        
        // When
        List<TaskStage> stages = stageFactory.buildStages(tenantConfig);
        
        // Then
        assertNotNull(stages);
        assertEquals(1, stages.size());
        
        TaskStage stage = stages.get(0);
        // Stage 名称格式：service-{serviceName}-{stageName}
        assertEquals("service-blue-green-gateway-deploy-stage", stage.getName());

        // 验证步骤数量和类型
        List<StageStep> steps = stage.getSteps();
        assertEquals(3, steps.size());
        assertTrue(steps.get(0) instanceof KeyValueWriteStep);
        assertTrue(steps.get(1) instanceof MessageBroadcastStep);
        assertTrue(steps.get(2) instanceof EndpointPollingStep);
        
        // 验证步骤名称
        assertTrue(steps.get(0).getStepName().startsWith("key-value-write"));
        assertTrue(steps.get(1).getStepName().startsWith("message-broadcast"));
        assertTrue(steps.get(2).getStepName().startsWith("endpoint-polling"));
    }
    
    @Test
    void shouldCreateStagesForPortal() {
        // Given
        TenantConfig tenantConfig = createPortalConfig();
        
        // When
        List<TaskStage> stages = stageFactory.buildStages(tenantConfig);
        
        // Then
        assertNotNull(stages);
        assertEquals(1, stages.size());
        
        TaskStage stage = stages.get(0);
        List<StageStep> steps = stage.getSteps();
        assertEquals(3, steps.size());
        
        // Portal 与蓝绿网关使用相同的步骤类型
        assertTrue(steps.get(0) instanceof KeyValueWriteStep);
        assertTrue(steps.get(1) instanceof MessageBroadcastStep);
        assertTrue(steps.get(2) instanceof EndpointPollingStep);
    }
    
    @Test
    void shouldCreateStagesForASBCGateway() {
        // Given
        TenantConfig tenantConfig = createASBCGatewayConfig();
        
        // When
        List<TaskStage> stages = stageFactory.buildStages(tenantConfig);
        
        // Then
        assertNotNull(stages);
        assertEquals(1, stages.size());
        
        TaskStage stage = stages.get(0);
        List<StageStep> steps = stage.getSteps();
        assertEquals(1, steps.size());
        
        // ASBC 只有一个步骤
        assertTrue(steps.get(0) instanceof ASBCConfigRequestStep);
        assertTrue(steps.get(0).getStepName().startsWith("asbc-config-request"));
    }
    
    @Test
    void shouldDetermineServiceTypeByMediaRoutingConfig() {
        // Given - 有 MediaRoutingConfig 的配置应该识别为 asbc-gateway
        TenantConfig tenantConfig = createASBCGatewayConfig();
        
        // When
        List<TaskStage> stages = stageFactory.buildStages(tenantConfig);
        
        // Then - 验证创建的是 ASBC 专用步骤
        assertEquals(1, stages.get(0).getSteps().size());
        assertTrue(stages.get(0).getSteps().get(0) instanceof ASBCConfigRequestStep);
    }
    
    @Test
    void shouldCreateStagesForMultipleServices() {
        // Given - 配置包含多个服务
        TenantConfig tenantConfig = new TenantConfig();
        tenantConfig.setTenantId("tenant-multi");
        tenantConfig.setDeployUnit(new DeployUnitIdentifier(400L, 1L, "multi-unit"));
        tenantConfig.setNacosNameSpace("test-ns");
        tenantConfig.setServiceNames(List.of("blue-green-gateway", "portal", "asbc-gateway"));  // 三个服务
        tenantConfig.setMediaRoutingConfig(new MediaRoutingConfig("trunk-001", "rules-001"));

        List<RouteRule> routeRules = List.of(RouteRule.of("k", "http://source", "http://target", "http://health", "http://hc"));
        tenantConfig.setRouteRules(routeRules);

        // When
        List<TaskStage> stages = stageFactory.buildStages(tenantConfig);

        // Then - 应该创建 3 个服务的 Stage
        assertNotNull(stages);
        assertEquals(3, stages.size());

        // 验证第一个 Stage（blue-green-gateway）
        TaskStage stage1 = stages.get(0);
        assertTrue(stage1.getName().contains("blue-green-gateway"));
        assertEquals(3, stage1.getSteps().size());

        // 验证第二个 Stage（portal）
        TaskStage stage2 = stages.get(1);
        assertTrue(stage2.getName().contains("portal"));
        assertEquals(3, stage2.getSteps().size());

        // 验证第三个 Stage（asbc-gateway）
        TaskStage stage3 = stages.get(2);
        assertTrue(stage3.getName().contains("asbc-gateway"));
        assertEquals(1, stage3.getSteps().size());
        assertTrue(stage3.getSteps().get(0) instanceof ASBCConfigRequestStep);
    }

    @Test
    void shouldHandleEmptyConfiguration() {
        // Given - 最小化的配置（包含所有必需字段）
        TenantConfig tenantConfig = new TenantConfig();
        tenantConfig.setTenantId("tenant-999");
        tenantConfig.setDeployUnit(new DeployUnitIdentifier(999L, 1L, "test-unit"));
        tenantConfig.setNacosNameSpace("test-namespace");  // 必需字段
        tenantConfig.setServiceNames(List.of("blue-green-gateway"));  // 指定服务

        // 设置最小的 NetworkEndpoint
        List<RouteRule> routeRules = List.of(RouteRule.of("k", "http://source", "http://target", "http://health", "http://hc"));
        tenantConfig.setRouteRules(routeRules);
        
        // When
        List<TaskStage> stages = stageFactory.buildStages(tenantConfig);
        
        // Then - 应该创建蓝绿网关的默认流程
        assertNotNull(stages);
        assertEquals(1, stages.size());
    }
    
    // ========== 辅助方法 ==========
    
    private TenantConfig createBlueGreenGatewayConfig() {
        TenantConfig config = new TenantConfig();
        config.setTenantId("tenant-001");
        config.setDeployUnit(new DeployUnitIdentifier(100L, 1L, "blue-green-unit"));
        config.setNacosNameSpace("test-ns");
        config.setServiceNames(List.of("blue-green-gateway"));  // 指定服务

        List<RouteRule> routeRules = List.of(RouteRule.of("k", "http://source", "http://target", "http://health", "http://hc"));
        config.setRouteRules(routeRules);
        
        return config;
    }
    
    private TenantConfig createPortalConfig() {
        TenantConfig config = new TenantConfig();
        config.setTenantId("tenant-002");
        config.setDeployUnit(new DeployUnitIdentifier(200L, 1L, "portal-unit"));
        config.setNacosNameSpace("test-ns");
        config.setServiceNames(List.of("portal"));  // 指定服务

        List<RouteRule> routeRules = List.of(RouteRule.of("k", "http://source", "http://target", "http://health", "http://hc"));
        config.setRouteRules(routeRules);
        
        return config;
    }
    
    private TenantConfig createASBCGatewayConfig() {
        TenantConfig config = new TenantConfig();
        config.setTenantId("tenant-003");
        config.setDeployUnit(new DeployUnitIdentifier(300L, 1L, "asbc-unit"));
        config.setServiceNames(List.of("asbc-gateway"));  // 指定服务

        // ASBC 的关键特征：MediaRoutingConfig
        config.setMediaRoutingConfig(
                new MediaRoutingConfig("trunk-group-001", "rules-001")
        );
        
        return config;
    }
}
