package xyz.firestige.deploy.testutil.factory;

import xyz.firestige.deploy.application.dto.DeployUnitIdentifier;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.vo.DeployVersion;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

import java.util.UUID;

/**
 * 值对象测试工厂
 * <p>
 * 用途：快速创建各类值对象，减少测试代码重复
 *
 * @since T-023 测试体系重建
 */
public class ValueObjectTestFactory {

    /**
     * 生成随机TaskId
     */
    public static TaskId randomTaskId() {
        return TaskId.of(UUID.randomUUID().toString());
    }

    /**
     * 生成指定前缀的TaskId
     */
    public static TaskId taskId(String prefix) {
        return TaskId.of(prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 生成固定值的TaskId（用于验证）
     */
    public static TaskId taskId(String id, boolean fixed) {
        return TaskId.of(fixed ? id : id + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 生成随机PlanId
     */
    public static PlanId randomPlanId() {
        return PlanId.of(UUID.randomUUID().toString());
    }

    /**
     * 生成指定前缀的PlanId
     */
    public static PlanId planId(String prefix) {
        return PlanId.of(prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 生成固定值的PlanId
     */
    public static PlanId planId(String id, boolean fixed) {
        return PlanId.of(fixed ? id : id + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 生成TenantId
     */
    public static TenantId tenantId(String id) {
        return TenantId.of(id);
    }

    /**
     * 生成随机TenantId
     */
    public static TenantId randomTenantId() {
        return TenantId.of("tenant-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 生成DeployVersion（从ID和版本号）
     */
    public static DeployVersion version(Long deployUnitId, Long deployUnitVersion) {
        return DeployVersion.of(deployUnitId, deployUnitVersion);
    }

    /**
     * 生成随机DeployVersion
     */
    public static DeployVersion randomVersion() {
        long timestamp = System.currentTimeMillis();
        return DeployVersion.of(100L, timestamp);
    }

    /**
     * 创建最小化TenantConfig（仅必填字段）
     */
    public static TenantConfig minimalConfig(TenantId tenantId) {
        TenantConfig config = new TenantConfig();
        config.setTenantId(tenantId.getValue());
        
        // 设置DeployUnit（必填）- record需要3个参数：id, version, name
        DeployUnitIdentifier deployUnit = new DeployUnitIdentifier(100L, 1L, "test-unit");
        config.setDeployUnit(deployUnit);
        
        // 设置serviceNames（必填）
        config.setServiceNames(java.util.List.of("test-service"));
        
        return config;
    }

    /**
     * 创建完整TenantConfig
     */
    public static TenantConfig fullConfig(TenantId tenantId, String serviceType) {
        TenantConfig config = minimalConfig(tenantId);
        config.setServiceNames(java.util.List.of(serviceType));
        return config;
    }

    /**
     * 创建用于测试的TenantConfig Builder
     */
    public static TenantConfigBuilder configBuilder() {
        return new TenantConfigBuilder();
    }

    /**
     * TenantConfig 流式构建器
     */
    public static class TenantConfigBuilder {
        private final TenantConfig config = new TenantConfig();

        public TenantConfigBuilder tenantId(String id) {
            config.setTenantId(id);
            return this;
        }

        public TenantConfigBuilder deployUnit(Long deployUnitId, Long version, String name) {
            DeployUnitIdentifier deployUnit = new DeployUnitIdentifier(deployUnitId, version, name);
            config.setDeployUnit(deployUnit);
            return this;
        }

        public TenantConfigBuilder serviceNames(String... names) {
            config.setServiceNames(java.util.Arrays.asList(names));
            return this;
        }

        public TenantConfigBuilder planId(Long planId) {
            config.setPlanId(planId);
            return this;
        }

        public TenantConfig build() {
            // 确保必填字段有默认值
            if (config.getTenantId() == null) {
                config.setTenantId("tenant-default");
            }
            if (config.getDeployUnit() == null) {
                config.setDeployUnit(new DeployUnitIdentifier(100L, 1L, "default-unit"));
            }
            if (config.getServiceNames() == null || config.getServiceNames().isEmpty()) {
                config.setServiceNames(java.util.List.of("default-service"));
            }
            return config;
        }
    }
}
