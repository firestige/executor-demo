package xyz.firestige.executor.exception;

/**
 * 配置下发异常
 * 配置下发器下发配置时发生错误
 */
public class ConfigDeployException extends TaskException {
    
    private final String serviceId;
    private final String tenantId;
    private final String deployerType;
    
    public ConfigDeployException(String message) {
        super(message);
        this.serviceId = null;
        this.tenantId = null;
        this.deployerType = null;
    }
    
    public ConfigDeployException(String message, Throwable cause) {
        super(message, cause);
        this.serviceId = null;
        this.tenantId = null;
        this.deployerType = null;
    }
    
    public ConfigDeployException(String serviceId, String tenantId, String deployerType, String message) {
        super(String.format("[Service: %s, Tenant: %s, Deployer: %s] %s", 
            serviceId, tenantId, deployerType, message));
        this.serviceId = serviceId;
        this.tenantId = tenantId;
        this.deployerType = deployerType;
    }
    
    public ConfigDeployException(String serviceId, String tenantId, String deployerType, 
                                String message, Throwable cause) {
        super(String.format("[Service: %s, Tenant: %s, Deployer: %s] %s", 
            serviceId, tenantId, deployerType, message), cause);
        this.serviceId = serviceId;
        this.tenantId = tenantId;
        this.deployerType = deployerType;
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public String getDeployerType() {
        return deployerType;
    }
}
