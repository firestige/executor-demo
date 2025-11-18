package xyz.firestige.deploy.util;

import xyz.firestige.entity.deploy.NetworkEndpoint;
import xyz.firestige.dto.deploy.TenantDeployConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 测试数据工厂
 * 提供快速创建测试数据的方法
 */
public class TestDataFactory {

    /**
     * 创建最小化配置（只包含必要字段）
     * 用于快速单元测试
     */
    public static TenantDeployConfig createMinimalConfig(String tenantId, Long planId) {
        TenantDeployConfig config = new TenantDeployConfig();
        config.setTenantId(tenantId);
        config.setPlanId(planId);
        config.setNetworkEndpoints(List.of(createMinimalEndpoint()));
        return config;
    }

    /**
     * 创建完整配置
     * 用于需要完整数据的测试
     */
    public static TenantDeployConfig createFullConfig(String tenantId, Long planId) {
        TenantDeployConfig config = new TenantDeployConfig();
        config.setTenantId(tenantId);
        config.setPlanId(planId);
        config.setDeployUnitId(1001L);
        config.setDeployUnitName("unit-" + tenantId);
        config.setNacosNameSpace("namespace-" + tenantId);

        List<NetworkEndpoint> endpoints = new ArrayList<>();
        endpoints.add(createFullEndpoint("192.168.1.1", "service1.example.com", "10.0.1.1"));
        endpoints.add(createFullEndpoint("192.168.1.2", "service2.example.com", "10.0.1.2"));
        config.setNetworkEndpoints(endpoints);

        return config;
    }

    /**
     * 创建指定数量的配置列表
     * 控制数量不超过 10 个，避免测试过慢
     */
    public static List<TenantDeployConfig> createConfigList(int count) {
        int actualCount = Math.min(count, 10); // 最多 10 个
        return IntStream.range(0, actualCount)
                .mapToObj(i -> createMinimalConfig("tenant" + i, 1001L))
                .collect(Collectors.toList());
    }

    /**
     * 创建指定数量的配置列表（指定 planId）
     */
    public static List<TenantDeployConfig> createConfigList(int count, Long planId) {
        int actualCount = Math.min(count, 10);
        return IntStream.range(0, actualCount)
                .mapToObj(i -> createMinimalConfig("tenant" + i, planId))
                .collect(Collectors.toList());
    }

    /**
     * 创建最小化网络端点
     */
    public static NetworkEndpoint createMinimalEndpoint() {
        NetworkEndpoint endpoint = new NetworkEndpoint();
        endpoint.setKey("endpoint-default");  // 必需字段
        endpoint.setSourceIp("192.168.1.1");
        endpoint.setSourceDomain("service.example.com");
        endpoint.setTargetIp("10.0.0.1");  // 必需字段
        endpoint.setTargetDomain("target.example.com");
        return endpoint;
    }

    /**
     * 创建完整网络端点
     */
    public static NetworkEndpoint createFullEndpoint(String sourceIp, String sourceDomain, String targetIp) {
        NetworkEndpoint endpoint = new NetworkEndpoint();
        endpoint.setSourceIp(sourceIp);
        endpoint.setSourceDomain(sourceDomain);
        endpoint.setTargetIp(targetIp != null ? targetIp : "10.0.0.1");
        endpoint.setTargetDomain("target.example.com");
        return endpoint;
    }

    /**
     * 创建无效配置（用于测试校验失败）
     */
    public static TenantDeployConfig createInvalidConfig() {
        TenantDeployConfig config = new TenantDeployConfig();
        // tenantId 为 null
        config.setPlanId(1001L);
        return config;
    }

    /**
     * 创建无效网络端点（用于测试校验失败）
     */
    public static NetworkEndpoint createInvalidEndpoint() {
        NetworkEndpoint endpoint = new NetworkEndpoint();
        // 缺少必要字段
        return endpoint;
    }
}

