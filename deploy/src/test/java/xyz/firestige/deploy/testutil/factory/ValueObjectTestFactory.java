package xyz.firestige.deploy.testutil.factory;

import com.github.javafaker.Faker;
import xyz.firestige.deploy.application.dto.DeployUnitIdentifier;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.vo.DeployVersion;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.RouteRule;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 值对象测试工厂
 * <p>
 * 用途：快速创建各类值对象，减少测试代码重复
 *
 * @since T-023 测试体系重建
 */
public class ValueObjectTestFactory {

    private static final Faker FAKER = new Faker();
    private static final String TASK_PREFIX = "task-";

    /**
     * 生成随机TaskId
     */
    public static TaskId randomTaskId() {
        return TaskId.of(String.format("%s%s", TASK_PREFIX, FAKER.idNumber().valid()));
    }

    /**
     * 生成指定前缀的TaskId
     */
    public static TaskId taskId(String prefix) {
        return TaskId.of(prefix + "-" + FAKER.idNumber().valid());
    }

    /**
     * 生成随机PlanId
     */
    public static PlanId randomPlanId() {
        return PlanId.of(FAKER.number().randomNumber());
    }

    /**
     * 生成随机TenantId
     */
    public static TenantId randomTenantId() {
        return TenantId.of(FAKER.internet().uuid());
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

    public static DeployUnitIdentifier randomDeployUnitIdentifier() {
        long deployUnitId = FAKER.number().randomNumber();
        long version = System.currentTimeMillis();
        String name = "deploy-unit-" + UUID.randomUUID();
        return new DeployUnitIdentifier(deployUnitId, version, name);
    }

    public static RouteRule randomRouteRule(int id) {
        return new RouteRule(String.valueOf(id), FAKER.internet().url());
    }

    /**
     * 创建最小化TenantConfig（仅必填字段）
     */
    public static TenantConfig minimalConfig() {
        TenantConfig config = new TenantConfig();
        config.setDeployUnit(randomDeployUnitIdentifier());
        config.setTenantId(randomTenantId());
        config.setPlanId(randomPlanId());
        config.setPlanVersion(FAKER.number().randomNumber());
        return config;
    }

    public static TenantConfig withPreviousConfig(TenantConfig previousConfig) {
        TenantConfig config = minimalConfig();
        config.setTenantId(previousConfig.getTenantId());
        config.setPreviousConfig(previousConfig);
        return config;
    }

    public static TenantConfig withPreviousConfig() {
        TenantConfig previousConfig = minimalConfig();
        return withPreviousConfig(previousConfig);
    }
}
