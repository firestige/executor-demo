package xyz.firestige.deploy.infrastructure.config.model;

import java.util.List;

/**
 * 部署配置根对象
 * 映射 deploy-stages.yml 的根结构
 *
 * @deprecated 已迁移到 InfrastructureProperties（T-027 Phase4）
 * 新配置使用 application.yml executor.infrastructure.*
 * 计划删除时间：v2.0
 */
@Deprecated
public class DeploymentConfig {
    
    private InfrastructureConfig infrastructure;

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


    public List<String> getDefaultServiceNames() {
        return defaultServiceNames;
    }

    public void setDefaultServiceNames(List<String> defaultServiceNames) {
        this.defaultServiceNames = defaultServiceNames;
    }
}
