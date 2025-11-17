package xyz.firestige.executor.application.dto;

import xyz.firestige.entity.deploy.NetworkEndpoint;

import java.util.List;

/**
 * 租户配置（内部 DTO）
 * 用于应用服务层，与外部 DTO（TenantDeployConfig）解耦
 * 只包含应用层业务逻辑需要的字段
 */
public class TenantConfig {

    // 核心标识
    private Long deployUnitId;
    private Long deployUnitVersion;
    private String deployUnitName;
    private String tenantId;

    // 健康检查相关
    private List<NetworkEndpoint> healthCheckEndpoints;

    // 配置相关
    private String nacosNameSpace;
    private Boolean defaultFlag;

    // Plan 相关
    private Long planId;
    private Long planVersion;

    // 回滚相关（上一次成功配置）
    private TenantConfig previousConfig;
    private Long previousConfigVersion;

    // 业务规则（如果应用层需要）
    private String calledNumberRules;
    private String trunkGroup;

    // 构造器
    public TenantConfig() {
    }

    // Getters and Setters

    public Long getDeployUnitId() {
        return deployUnitId;
    }

    public void setDeployUnitId(Long deployUnitId) {
        this.deployUnitId = deployUnitId;
    }

    public Long getDeployUnitVersion() {
        return deployUnitVersion;
    }

    public void setDeployUnitVersion(Long deployUnitVersion) {
        this.deployUnitVersion = deployUnitVersion;
    }

    public String getDeployUnitName() {
        return deployUnitName;
    }

    public void setDeployUnitName(String deployUnitName) {
        this.deployUnitName = deployUnitName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public List<NetworkEndpoint> getHealthCheckEndpoints() {
        return healthCheckEndpoints;
    }

    public void setHealthCheckEndpoints(List<NetworkEndpoint> healthCheckEndpoints) {
        this.healthCheckEndpoints = healthCheckEndpoints;
    }

    public String getNacosNameSpace() {
        return nacosNameSpace;
    }

    public void setNacosNameSpace(String nacosNameSpace) {
        this.nacosNameSpace = nacosNameSpace;
    }

    public Boolean getDefaultFlag() {
        return defaultFlag;
    }

    public void setDefaultFlag(Boolean defaultFlag) {
        this.defaultFlag = defaultFlag;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public Long getPlanVersion() {
        return planVersion;
    }

    public void setPlanVersion(Long planVersion) {
        this.planVersion = planVersion;
    }

    public TenantConfig getPreviousConfig() {
        return previousConfig;
    }

    public void setPreviousConfig(TenantConfig previousConfig) {
        this.previousConfig = previousConfig;
    }

    public Long getPreviousConfigVersion() {
        return previousConfigVersion;
    }

    public void setPreviousConfigVersion(Long previousConfigVersion) {
        this.previousConfigVersion = previousConfigVersion;
    }

    public String getCalledNumberRules() {
        return calledNumberRules;
    }

    public void setCalledNumberRules(String calledNumberRules) {
        this.calledNumberRules = calledNumberRules;
    }

    public String getTrunkGroup() {
        return trunkGroup;
    }

    public void setTrunkGroup(String trunkGroup) {
        this.trunkGroup = trunkGroup;
    }

    @Override
    public String toString() {
        return "TenantConfig{" +
                "deployUnitId=" + deployUnitId +
                ", deployUnitVersion=" + deployUnitVersion +
                ", deployUnitName='" + deployUnitName + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", planId=" + planId +
                '}';
    }
}

