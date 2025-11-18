package xyz.firestige.deploy.facade.converter;

import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.application.dto.DeployUnitIdentifier;
import xyz.firestige.deploy.application.dto.MediaRoutingConfig;
import xyz.firestige.deploy.application.dto.TenantConfig;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户配置转换器（防腐层）
 * <p>
 * 职责：
 * - 将外部 DTO (TenantDeployConfig) 转换为内部 DTO (TenantConfig)
 * - 隔离外部依赖，保护内部模型
 *
 * @since DDD 重构 Phase 2.3
 */
public class TenantConfigConverter {

    /**
     * 批量转换
     *
     * @param externalConfigs 外部配置列表
     * @return 内部配置列表
     */
    public static List<TenantConfig> fromExternal(List<TenantDeployConfig> externalConfigs) {
        if (externalConfigs == null) {
            return List.of();
        }
        return externalConfigs.stream()
                .map(TenantConfigConverter::convert)
                .collect(Collectors.toList());
    }

    /**
     * 单个转换
     *
     * @param external 外部配置
     * @return 内部配置
     */
    public static TenantConfig convert(TenantDeployConfig external) {
        if (external == null) {
            return null;
        }

        TenantConfig internal = new TenantConfig();

        // 基本标识
        internal.setTenantId(external.getTenantId());
        internal.setPlanId(external.getPlanId());

        // 部署单元信息
        if (external.getDeployUnitName() != null) {
            DeployUnitIdentifier deployUnit = new DeployUnitIdentifier(
                external.getPlanId(),
                external.getDeployUnitVersion(),
                external.getDeployUnitName()
            );
            internal.setDeployUnit(deployUnit);
        }

        // 网络端点
        internal.setNetworkEndpoints(external.getNetworkEndpoints());

        // 健康检查端点（如果外部有提供的话）
        // 注意：healthCheckEndpoints 可能需要从 networkEndpoints 派生
        // 这里简化处理，如果需要可以添加逻辑

        // Nacos 配置
        internal.setNacosNameSpace(external.getNacosNameSpace());
        internal.setDefaultFlag(external.getDefaultFlag());

        // 媒体路由配置
        if (external.getCalledNumberRules() != null && external.getTrunkGroup()!= null) {
            MediaRoutingConfig mediaConfig =
                new MediaRoutingConfig(
                    external.getTrunkGroup(),
                    external.getCalledNumberRules()
                );
            internal.setMediaRoutingConfig(mediaConfig);
        }

        return internal;
    }
}

