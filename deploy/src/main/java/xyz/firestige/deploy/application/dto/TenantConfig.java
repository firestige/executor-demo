package xyz.firestige.deploy.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.RouteRule;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.entity.deploy.NetworkEndpoint;


import java.util.ArrayList;
import java.util.List;

/**
 * 租户配置（内部 DTO）
 * 用于应用服务层，与外部 DTO（TenantDeployConfig）解耦
 * 只包含应用层业务逻辑需要的字段
 */
public class TenantConfig {

    // 核心标识（使用 record 组合）
    @NotNull(message = "部署单元不能为空")
    @Valid
    private DeployUnitIdentifier deployUnit;

    @NotNull(message = "租户ID不能为空或空白")
    private TenantId tenantId;

    // HTTP 网关路由信息
    private List<RouteRule> routeRules = new ArrayList<>();

    // 健康检查端点
    // 基于服务约定，优先级：参数 > 配置 > 默认值
    // 由 Facade 或转换工厂类装配
    private List<String> healthCheckEndpoints;

    // 配置相关
    private String nacosNameSpace;
    private Boolean defaultFlag;

    // Plan 相关
    private PlanId planId;
    private Long planVersion;

    // 回滚相关
    // previousConfig: 上一次成功的完整配置（用于回滚时恢复配置内容）
    // previousConfigVersion: 冗余字段，快速访问上一次的版本号
    //   用途：回滚时创建新版本号，保证业务端基于版本号的幂等操作不失效
    //   设计：虽然可以通过 previousConfig.getDeployUnit().version() 获取，
    //        但单独字段避免频繁访问，提升可读性
    private TenantConfig previousConfig;
    private Long previousConfigVersion;

    // 媒体路由配置（使用 record 保证配对约束）
    private MediaRoutingConfig mediaRoutingConfig;

    /**
     * 需要切换的服务名称列表（有序）
     * 定义该租户需要执行哪些服务的切换操作
     *
     * 例如: ["blue-green-gateway", "portal", "asbc-gateway"]
     *
     * 顺序很重要：将按照列表顺序依次创建 Stage
     * 由 TenantConfigConverter 负责填充（显式传入或使用默认值）
     */
    @NotNull(message = "服务名称列表不能为空")
    private List<String> serviceNames;

    // 构造器
    public TenantConfig() {
    }

    // Getters and Setters

    public DeployUnitIdentifier getDeployUnit() {
        return deployUnit;
    }

    public void setDeployUnit(DeployUnitIdentifier deployUnit) {
        this.deployUnit = deployUnit;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = TenantId.of(tenantId);
    }

    public List<RouteRule> getRouteRules() {
        return routeRules;
    }

    public void setRouteRules(List<RouteRule> routeRules) {
        this.routeRules = routeRules;
    }

    public List<String> getHealthCheckEndpoints() {
        return healthCheckEndpoints;
    }

    public void setHealthCheckEndpoints(List<String> healthCheckEndpoints) {
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

    public PlanId getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = PlanId.of(planId);
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

    public MediaRoutingConfig getMediaRoutingConfig() {
        return mediaRoutingConfig;
    }

    public void setMediaRoutingConfig(MediaRoutingConfig mediaRoutingConfig) {
        this.mediaRoutingConfig = mediaRoutingConfig;
    }

    public List<String> getServiceNames() {
        return serviceNames;
    }

    public void setServiceNames(List<String> serviceNames) {
        this.serviceNames = serviceNames;
    }

    // 便利方法：兼容旧的 API（可选，Phase 3 实施时根据需要决定是否保留）

    public Long getDeployUnitId() {
        return deployUnit != null ? deployUnit.id() : null;
    }

    public Long getDeployUnitVersion() {
        return deployUnit != null ? deployUnit.version() : null;
    }

    public String getDeployUnitName() {
        return deployUnit != null ? deployUnit.name() : null;
    }

    @Override
    public String toString() {
        return "TenantConfig{" +
                "deployUnit=" + deployUnit +
                ", tenantId='" + tenantId + '\'' +
                ", planId=" + planId +
                ", mediaRoutingConfig=" + mediaRoutingConfig +
                '}';
    }
}

