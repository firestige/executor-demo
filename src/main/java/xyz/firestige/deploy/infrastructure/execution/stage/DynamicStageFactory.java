package xyz.firestige.deploy.infrastructure.execution.stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;
import xyz.firestige.deploy.domain.stage.factory.ServiceConfigFactoryComposite;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.config.model.ServiceTypeConfig;
import xyz.firestige.deploy.infrastructure.config.model.StageDefinition;
import xyz.firestige.deploy.infrastructure.config.model.StepDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态 Stage 工厂（配置驱动）
 * 
 * 职责：
 * 1. 读取 YAML 配置
 * 2. 使用防腐层转换 TenantConfig → ServiceConfig
 * 3. 基于配置动态创建 Stage 和 Step
 * 
 * 设计：
 * - 配置来源：deploy-stages.yml
 * - 防腐层：ServiceConfigFactoryComposite
 * - 步骤创建：StepRegistry
 */
@Component
public class DynamicStageFactory implements StageFactory {
    
    private static final Logger log = LoggerFactory.getLogger(DynamicStageFactory.class);
    
    private final ServiceConfigFactoryComposite configFactory;
    private final DeploymentConfigLoader configLoader;
    private final StepRegistry stepRegistry;
    
    public DynamicStageFactory(
            ServiceConfigFactoryComposite configFactory,
            DeploymentConfigLoader configLoader,
            StepRegistry stepRegistry) {
        
        this.configFactory = configFactory;
        this.configLoader = configLoader;
        this.stepRegistry = stepRegistry;
        
        log.info("DynamicStageFactory initialized with config-driven stage building");
    }
    
    @Override
    public List<TaskStage> buildStages(TenantConfig tenantConfig) {
        // 1. 确定服务类型（从 TenantConfig 推断）
        String serviceType = determineServiceType(tenantConfig);
        
        log.info("Building stages for service type: {} (tenant={})", 
                serviceType, tenantConfig.getTenantId());
        
        // 2. 通过防腐层转换为领域配置
        ServiceConfig serviceConfig = configFactory.createConfig(serviceType, tenantConfig);
        
        // 3. 从 YAML 读取服务类型定义
        ServiceTypeConfig serviceTypeConfig = configLoader.getServiceType(serviceType);
        if (serviceTypeConfig == null) {
            throw new UnsupportedOperationException("Service type not configured: " + serviceType);
        }
        
        // 4. 动态构建 Stage 列表
        List<TaskStage> stages = new ArrayList<>();
        for (StageDefinition stageDef : serviceTypeConfig.getStages()) {
            TaskStage stage = buildStage(stageDef, serviceConfig);
            stages.add(stage);
            
            log.info("Created stage: name={}, steps={}", stageDef.getName(), stageDef.getSteps().size());
        }
        
        log.info("Total stages built: {}", stages.size());
        return stages;
    }
    
    /**
     * 构建单个 Stage
     */
    private TaskStage buildStage(StageDefinition stageDef, ServiceConfig serviceConfig) {
        List<StageStep> steps = new ArrayList<>();
        
        for (StepDefinition stepDef : stageDef.getSteps()) {
            StageStep step = stepRegistry.createStep(stepDef, serviceConfig);
            steps.add(step);
        }
        
        return new CompositeServiceStage(stageDef.getName(), steps);
    }
    
    /**
     * 从 TenantConfig 推断服务类型
     * 
     * 规则：
     * 1. 如果有 mediaRoutingConfig 且已启用 → asbc-gateway
     * 2. 默认 → blue-green-gateway
     * 
     * TODO: 后续可以在 TenantConfig 中显式添加 serviceType 字段
     */
    private String determineServiceType(TenantConfig tenantConfig) {
        // 规则 1：ASBC 网关特征检测
        if (tenantConfig.getMediaRoutingConfig() != null && 
            tenantConfig.getMediaRoutingConfig().isEnabled()) {
            return "asbc-gateway";
        }
        
        // TODO: 可以根据其他字段进一步推断 portal vs blue-green-gateway
        // 例如：根据 deployUnit.name 或专门的 serviceType 字段
        
        // 默认：蓝绿网关
        return "blue-green-gateway";
    }
}
