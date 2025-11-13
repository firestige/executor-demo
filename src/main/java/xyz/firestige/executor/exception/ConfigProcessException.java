package xyz.firestige.executor.exception;

/**
 * 配置处理异常
 * 配置处理器处理配置时发生错误
 */
public class ConfigProcessException extends TaskException {
    
    private final String serviceId;
    private final String tenantId;
    
    public ConfigProcessException(String message) {
        super(message);
        this.serviceId = null;
        this.tenantId = null;
    }
    
    public ConfigProcessException(String message, Throwable cause) {
        super(message, cause);
        this.serviceId = null;
        this.tenantId = null;
    }
    
    public ConfigProcessException(String serviceId, String tenantId, String message) {
        super(String.format("[Service: %s, Tenant: %s] %s", serviceId, tenantId, message));
        this.serviceId = serviceId;
        this.tenantId = tenantId;
    }
    
    public ConfigProcessException(String serviceId, String tenantId, String message, Throwable cause) {
        super(String.format("[Service: %s, Tenant: %s] %s", serviceId, tenantId, message), cause);
        this.serviceId = serviceId;
        this.tenantId = tenantId;
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public String getTenantId() {
        return tenantId;
    }
}
