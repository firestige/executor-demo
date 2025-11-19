package xyz.firestige.deploy.infrastructure.config.model;

import java.util.List;
import java.util.Map;

/**
 * 部署配置根对象
 * 映射 deploy-stages.yml 的根结构
 */
public class DeploymentConfig {
    
    private InfrastructureConfig infrastructure;

    /**
     * 服务配置映射（key = ServiceName，如 "blue-green-gateway"）
     * 同时支持 services 和 serviceTypes 两种配置名称（兼容性）
     */
    private Map<String, ServiceTypeConfig> services;
    private Map<String, ServiceTypeConfig> serviceTypes;  // 兼容旧配置

    /**
     * 默认服务名称列表（有序）
     * 当租户配置未指定 serviceNames 时使用
     */
    private List<String> defaultServiceNames;

    public InfrastructureConfig getInfrastructure() {
        return infrastructure;
    }
    
    public void setInfrastructure(InfrastructureConfig infrastructure) {
        this.infrastructure = infrastructure;
    }
    
    /**
     * 获取服务配置（优先使用 services，兼容 serviceTypes）
     */
    public Map<String, ServiceTypeConfig> getServices() {
        return services != null ? services : serviceTypes;
    }

    public void setServices(Map<String, ServiceTypeConfig> services) {
        this.services = services;
    }

    public Map<String, ServiceTypeConfig> getServiceTypes() {
        return serviceTypes;
    }
    
    public void setServiceTypes(Map<String, ServiceTypeConfig> serviceTypes) {
        this.serviceTypes = serviceTypes;
    }

    public List<String> getDefaultServiceNames() {
        return defaultServiceNames;
    }

    public void setDefaultServiceNames(List<String> defaultServiceNames) {
        this.defaultServiceNames = defaultServiceNames;
    }
}
