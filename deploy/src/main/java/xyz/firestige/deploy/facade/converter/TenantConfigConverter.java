package xyz.firestige.deploy.facade.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.shared.vo.RouteRule;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.application.dto.DeployUnitIdentifier;
import xyz.firestige.deploy.application.dto.MediaRoutingConfig;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户配置转换器（防腐层）
 * <p>
 * 职责：
 * - 将外部 DTO (TenantDeployConfig) 转换为内部 DTO (TenantConfig)
 * - 隔离外部依赖，保护内部模型
 * - 解析服务名称列表（显式传入或使用默认值）
 *
 * @since DDD 重构 Phase 2.3
 */
@Component
public class TenantConfigConverter {

    private static final Logger log = LoggerFactory.getLogger(TenantConfigConverter.class);

    private final DeploymentConfigLoader configLoader;

    public TenantConfigConverter(DeploymentConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    /**
     * 批量转换
     *
     * @param externalConfigs 外部配置列表
     * @return 内部配置列表
     */
    public List<TenantConfig> fromExternal(List<TenantDeployConfig> externalConfigs) {
        if (externalConfigs == null) {
            return List.of();
        }
        return externalConfigs.stream()
                .map(this::convert)
                .collect(Collectors.toList());
    }

    /**
     * 单个转换
     *
     * @param external 外部配置
     * @return 内部配置
     */
    public TenantConfig convert(TenantDeployConfig external) {
        if (external == null) {
            return null;
        }

        TenantConfig internal = new TenantConfig();

        // 基本标识
        internal.setTenantId(external.getTenantId());
        internal.setPlanId(external.getPlanId());
        internal.setPlanVersion(external.getPlanVersion());

        // 部署单元信息
        if (external.getDeployUnitName() != null) {
            DeployUnitIdentifier deployUnit = new DeployUnitIdentifier(
                external.getPlanId(),
                external.getDeployUnitVersion(),
                external.getDeployUnitName()
            );
            internal.setDeployUnit(deployUnit);
        }

        // 路由规则
        List<RouteRule> rules = external.getNetworkEndpoints().stream()
                .map(ep -> RouteRule.of(
                        ep.getKey(),
                        ep.getSourceIp(),
                        ep.getSourceDomain(),
                        ep.getTargetIp(),
                        ep.getTargetDomain()))
                .toList();
        internal.setRouteRules(rules);


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

        // 服务名称列表：优先使用外部传入的值，否则使用配置文件默认值
        List<String> serviceNames = resolveServiceNames(external);
        internal.setServiceNames(serviceNames);

        log.debug("Resolved service names for tenant {}: {}",
                external.getTenantId(), serviceNames);

        return internal;
    }

    /**
     * 解析服务名称列表
     *
     * 策略：
     * 1. 如果外部显式传入 → 直接使用
     * 2. 否则 → 使用配置文件默认值
     */
    private List<String> resolveServiceNames(TenantDeployConfig external) {
        // 策略 1：显式指定
//        if (external.getServiceNames() != null && !external.getServiceNames().isEmpty()) {
//            log.debug("Using explicitly provided service names for tenant {}", external.getTenantId());
//            return new ArrayList<>(external.getServiceNames());
//        }

        // 策略 2：使用配置文件默认值
        List<String> defaultNames = configLoader.getDefaultServiceNames();
        log.debug("Using default service names for tenant {}: {}",
                external.getTenantId(), defaultNames);
        return new ArrayList<>(defaultNames);
    }
}

