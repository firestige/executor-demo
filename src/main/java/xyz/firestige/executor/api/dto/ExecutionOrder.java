package xyz.firestige.executor.api.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行单
 * 包含蓝绿环境切换任务的所有输入信息
 */
public class ExecutionOrder {
    /**
     * 目标环境（如：blue, green, production, staging等）
     */
    private String targetEnvironment;
    
    /**
     * 租户ID列表
     * 本次切换涉及的所有租户
     */
    private List<String> tenantIds;
    
    /**
     * 服务配置列表（按执行顺序排列）
     * 当前版本按顺序执行，远期支持DAG
     */
    private List<ServiceConfig> serviceConfigs;
    
    /**
     * 全局配置下发策略（默认为并发）
     * 可被单个服务的策略覆盖
     */
    private DeployStrategy globalDeployStrategy;
    
    /**
     * 原始配置数据
     * 存储未经处理的原始配置，需要通过配置处理器派生出实际可用的配置
     */
    private Map<String, Object> rawConfig;
    
    /**
     * 执行单元的超时时间（毫秒）
     */
    private Long timeoutMillis;
    
    /**
     * 创建者标识
     */
    private String createdBy;
    
    /**
     * 执行单描述
     */
    private String description;
    
    /**
     * 扩展属性
     */
    private Map<String, Object> attributes;
    
    public ExecutionOrder() {
        this.tenantIds = new ArrayList<>();
        this.serviceConfigs = new ArrayList<>();
        this.rawConfig = new HashMap<>();
        this.attributes = new HashMap<>();
        this.globalDeployStrategy = DeployStrategy.CONCURRENT; // 默认并发
    }
    
    /**
     * 验证执行单的有效性
     */
    public boolean isValid() {
        if (targetEnvironment == null || targetEnvironment.trim().isEmpty()) {
            return false;
        }
        if (tenantIds == null || tenantIds.isEmpty()) {
            return false;
        }
        return !serviceConfigs.isEmpty();
    }
    
    /**
     * 添加服务配置
     */
    public void addServiceConfig(ServiceConfig serviceConfig) {
        if (this.serviceConfigs == null) {
            this.serviceConfigs = new ArrayList<>();
        }
        this.serviceConfigs.add(serviceConfig);
    }
    
    /**
     * 添加租户ID
     */
    public void addTenantId(String tenantId) {
        if (this.tenantIds == null) {
            this.tenantIds = new ArrayList<>();
        }
        if (!this.tenantIds.contains(tenantId)) {
            this.tenantIds.add(tenantId);
        }
    }
    
    // Getters and Setters
    
    public String getTargetEnvironment() {
        return targetEnvironment;
    }
    
    public void setTargetEnvironment(String targetEnvironment) {
        this.targetEnvironment = targetEnvironment;
    }
    
    public List<String> getTenantIds() {
        return tenantIds;
    }
    
    public void setTenantIds(List<String> tenantIds) {
        this.tenantIds = tenantIds;
    }
    
    public List<ServiceConfig> getServiceConfigs() {
        return serviceConfigs;
    }
    
    public void setServiceConfigs(List<ServiceConfig> serviceConfigs) {
        this.serviceConfigs = serviceConfigs;
    }
    
    public DeployStrategy getGlobalDeployStrategy() {
        return globalDeployStrategy;
    }
    
    public void setGlobalDeployStrategy(DeployStrategy globalDeployStrategy) {
        this.globalDeployStrategy = globalDeployStrategy;
    }
    
    public Map<String, Object> getRawConfig() {
        return rawConfig;
    }
    
    public void setRawConfig(Map<String, Object> rawConfig) {
        this.rawConfig = rawConfig;
    }
    
    public void putRawConfig(String key, Object value) {
        this.rawConfig.put(key, value);
    }
    
    public Object getRawConfig(String key) {
        return this.rawConfig.get(key);
    }
    
    public Long getTimeoutMillis() {
        return timeoutMillis;
    }
    
    public void setTimeoutMillis(Long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
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
        return "ExecutionOrder{" +
                "targetEnvironment='" + targetEnvironment + '\'' +
                ", tenantIds=" + tenantIds +
                ", serviceConfigs=" + serviceConfigs.size() + " services" +
                ", globalDeployStrategy=" + globalDeployStrategy +
                ", description='" + description + '\'' +
                '}';
    }
}
