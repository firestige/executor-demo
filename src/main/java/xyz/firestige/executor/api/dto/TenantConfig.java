package xyz.firestige.executor.api.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 租户配置
 * 定义单个租户的配置信息（可选，如果租户信息简单可以只用String表示）
 */
public class TenantConfig {
    /**
     * 租户唯一标识
     */
    private String tenantId;
    
    /**
     * 租户名称
     */
    private String tenantName;
    
    /**
     * 租户特定的配置数据
     */
    private Map<String, Object> configData;
    
    /**
     * 扩展属性
     */
    private Map<String, Object> attributes;
    
    public TenantConfig() {
        this.configData = new HashMap<>();
        this.attributes = new HashMap<>();
    }
    
    public TenantConfig(String tenantId) {
        this();
        this.tenantId = tenantId;
    }
    
    public TenantConfig(String tenantId, String tenantName) {
        this(tenantId);
        this.tenantName = tenantName;
    }
    
    // Getters and Setters
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public String getTenantName() {
        return tenantName;
    }
    
    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
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
        return "TenantConfig{" +
                "tenantId='" + tenantId + '\'' +
                ", tenantName='" + tenantName + '\'' +
                '}';
    }
}
