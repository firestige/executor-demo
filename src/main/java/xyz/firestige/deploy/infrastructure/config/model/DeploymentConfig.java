package xyz.firestige.deploy.infrastructure.config.model;

import java.util.Map;

/**
 * 部署配置根对象
 * 映射 deploy-stages.yml 的根结构
 */
public class DeploymentConfig {
    
    private InfrastructureConfig infrastructure;
    private Map<String, ServiceTypeConfig> serviceTypes;
    
    public InfrastructureConfig getInfrastructure() {
        return infrastructure;
    }
    
    public void setInfrastructure(InfrastructureConfig infrastructure) {
        this.infrastructure = infrastructure;
    }
    
    public Map<String, ServiceTypeConfig> getServiceTypes() {
        return serviceTypes;
    }
    
    public void setServiceTypes(Map<String, ServiceTypeConfig> serviceTypes) {
        this.serviceTypes = serviceTypes;
    }
}
