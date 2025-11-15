package xyz.firestige.dto.deploy;

import xyz.firestige.entity.deploy.NetworkEndpoint;

import java.util.List;

public class TenantDeployConfig {
    private Long deployUnitId;
    private Long deployUnitVersion;
    private String deployUnitName;
    private List<NetworkEndpoint> networkEndpoints;
    private String nacosNameSpace;
    private Boolean defaultFlag;
    private String tenantId;
    private String calledNumberRules;
    private String trunkGroup;
    private Long planId;
    private Long planVersion;
    private TenantDeployConfig sourceTenantDeployConfig;

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

    public List<NetworkEndpoint> getNetworkEndpoints() {
        return networkEndpoints;
    }

    public void setNetworkEndpoints(List<NetworkEndpoint> networkEndpoints) {
        this.networkEndpoints = networkEndpoints;
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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

    public TenantDeployConfig getSourceTenantDeployConfig() {
        return sourceTenantDeployConfig;
    }

    public void setSourceTenantDeployConfig(TenantDeployConfig sourceTenantDeployConfig) {
        this.sourceTenantDeployConfig = sourceTenantDeployConfig;
    }
}
