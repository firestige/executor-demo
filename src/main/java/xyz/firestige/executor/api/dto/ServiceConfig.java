package xyz.firestige.executor.api.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务配置
 * 定义单个服务的配置信息和下发策略
 */
public class ServiceConfig {
    /**
     * 服务唯一标识
     */
    private String serviceId;
    
    /**
     * 服务名称
     */
    private String serviceName;
    
    /**
     * 配置下发策略（可选，如果未设置则使用执行单的全局策略）
     */
    private DeployStrategy deployStrategy;
    
    /**
     * 最大并发数（仅当 deployStrategy=CONCURRENT 时生效）
     * null 表示不限制并发数
     */
    private Integer maxConcurrency;
    
    /**
     * 超时时间（毫秒）
     */
    private Long timeoutMillis;
    
    /**
     * 服务特定的配置数据
     * 存储该服务需要的配置内容，由配置处理器处理
     */
    private Map<String, Object> configData;
    
    /**
     * 扩展属性
     */
    private Map<String, Object> attributes;
    
    public ServiceConfig() {
        this.configData = new HashMap<>();
        this.attributes = new HashMap<>();
    }
    
    public ServiceConfig(String serviceId) {
        this();
        this.serviceId = serviceId;
    }
    
    public ServiceConfig(String serviceId, String serviceName) {
        this(serviceId);
        this.serviceName = serviceName;
    }
    
    // Getters and Setters
    
    public String getServiceId() {
        return serviceId;
    }
    
    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public DeployStrategy getDeployStrategy() {
        return deployStrategy;
    }
    
    public void setDeployStrategy(DeployStrategy deployStrategy) {
        this.deployStrategy = deployStrategy;
    }
    
    public Integer getMaxConcurrency() {
        return maxConcurrency;
    }
    
    public void setMaxConcurrency(Integer maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }
    
    public Long getTimeoutMillis() {
        return timeoutMillis;
    }
    
    public void setTimeoutMillis(Long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
    
    public Map<String, Object> getConfigData() {
        return configData;
    }
    
    public void setConfigData(Map<String, Object> configData) {
        this.configData = configData;
    }
    
    public void putConfigData(String key, Object value) {
        this.configData.put(key, value);
    }
    
    public Object getConfigData(String key) {
        return this.configData.get(key);
    }
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
    
    public void putAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }
    
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }
    
    @Override
    public String toString() {
        return "ServiceConfig{" +
                "serviceId='" + serviceId + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", deployStrategy=" + deployStrategy +
                ", maxConcurrency=" + maxConcurrency +
                ", timeoutMillis=" + timeoutMillis +
                '}';
    }
}
