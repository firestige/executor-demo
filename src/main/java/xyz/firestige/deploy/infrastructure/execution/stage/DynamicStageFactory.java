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
        // 1. 获取服务名称列表（有序）
        List<String> serviceNames = tenantConfig.getServiceNames();

        if (serviceNames == null || serviceNames.isEmpty()) {
            throw new IllegalStateException(
                String.format("Service names not configured for tenant: %s", tenantConfig.getTenantId()));
        }

        log.info("Building stages for {} services (tenant={}): {}",
                serviceNames.size(), tenantConfig.getTenantId(), serviceNames);

        // 2. 遍历服务列表，为每个服务构建 Stage
        List<TaskStage> allStages = new ArrayList<>();

        for (String serviceName : serviceNames) {
            try {
                // 2.1 从 YAML 读取服务配置模板
                ServiceTypeConfig serviceTypeConfig = configLoader.getServiceConfig(serviceName);
                if (serviceTypeConfig == null) {
                    throw new UnsupportedOperationException(
                        String.format("Service not configured in deploy-stages.yml: %s", serviceName));
                }

                // 2.2 通过防腐层转换为领域服务配置（注入租户特定信息）
                ServiceConfig serviceConfig = configFactory.createConfig(serviceName, tenantConfig);

                // 2.3 构建该服务的所有 Stage
                List<TaskStage> serviceStages = buildStagesForService(
                    serviceName,
                    serviceTypeConfig,
                    serviceConfig
                );
                allStages.addAll(serviceStages);

                log.info("Created {} stage(s) for service: {}",
                        serviceStages.size(), serviceName);

            } catch (Exception e) {
                log.error("Failed to build stages for service: {}", serviceName, e);
                throw new IllegalStateException(
                    String.format("Failed to build stages for service '%s': %s",
                            serviceName, e.getMessage()), e);
            }
        }
        
        log.info("Total stages built: {} for {} services (tenant={})",
                allStages.size(), serviceNames.size(), tenantConfig.getTenantId());
        return allStages;
    }

    /**
     * 为单个服务构建所有 Stage
     *
     * @param serviceName 服务名称
     * @param serviceTypeConfig YAML 配置模板
     * @param serviceConfig 领域服务配置
     * @return 该服务的所有 Stage 列表
     */
    private List<TaskStage> buildStagesForService(
            String serviceName,
            ServiceTypeConfig serviceTypeConfig,
            ServiceConfig serviceConfig) {

        List<TaskStage> stages = new ArrayList<>();

        for (StageDefinition stageDef : serviceTypeConfig.getStages()) {
            TaskStage stage = buildStage(serviceName, stageDef, serviceConfig);
            stages.add(stage);
            
            log.debug("Created stage: service={}, name={}, steps={}",
                    serviceName, stageDef.getName(), stageDef.getSteps().size());
        }
        
        return stages;
    }
    
    /**
     * 构建单个 Stage
     *
     * @param serviceName 服务名称
     * @param stageDef Stage 定义
     * @param serviceConfig 服务配置
     * @return TaskStage
     */
    private TaskStage buildStage(String serviceName, StageDefinition stageDef, ServiceConfig serviceConfig) {
        List<StageStep> steps = new ArrayList<>();
        
        for (StepDefinition stepDef : stageDef.getSteps()) {
            StageStep step = stepRegistry.createStep(stepDef, serviceConfig);
            steps.add(step);
        }
        
        // Stage 名称：service-{serviceName}-{stageName}
        // 例如：service-blue-green-gateway-deploy-stage
        String stageName = String.format("service-%s-%s", serviceName, stageDef.getName());
        return new CompositeServiceStage(stageName, steps);
    }
}
