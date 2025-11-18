package xyz.firestige.deploy.unit.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.entity.deploy.NetworkEndpoint;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.util.TestDataFactory;
import xyz.firestige.deploy.util.TimingExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * TestDataFactory 单元测试
 * 测试测试数据工厂
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("TestDataFactory 单元测试")
class TestDataFactoryTest {

    @Test
    @DisplayName("场景: 创建最小化配置")
    void testCreateMinimalConfig() {
        // When: 创建最小化配置
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);

        // Then: 配置正确
        assertNotNull(config);
        assertEquals("tenant1", config.getTenantId());
        assertEquals(1001L, config.getPlanId());
        assertNotNull(config.getNetworkEndpoints());
        assertFalse(config.getNetworkEndpoints().isEmpty());
    }

    @Test
    @DisplayName("场景: 创建配置列表")
    void testCreateConfigList() {
        // When: 创建3个配置
        List<TenantDeployConfig> configs = TestDataFactory.createConfigList(3);

        // Then: 列表正确
        assertNotNull(configs);
        assertEquals(3, configs.size());
        // 验证租户ID存在且不同
        String tenantId0 = configs.get(0).getTenantId();
        String tenantId1 = configs.get(1).getTenantId();
        String tenantId2 = configs.get(2).getTenantId();
        assertNotNull(tenantId0);
        assertNotNull(tenantId1);
        assertNotNull(tenantId2);
        assertNotEquals(tenantId0, tenantId1);
        assertNotEquals(tenantId1, tenantId2);
    }

    @Test
    @DisplayName("场景: 创建带planId的配置列表")
    void testCreateConfigListWithPlanId() {
        // When: 创建带指定planId的配置列表
        List<TenantDeployConfig> configs = TestDataFactory.createConfigList(2, 2001L);

        // Then: planId正确
        assertEquals(2, configs.size());
        assertEquals(2001L, configs.get(0).getPlanId());
        assertEquals(2001L, configs.get(1).getPlanId());
    }

    @Test
    @DisplayName("场景: 创建最小化端点")
    void testCreateMinimalEndpoint() {
        // When: 创建最小化端点
        NetworkEndpoint endpoint = TestDataFactory.createMinimalEndpoint();

        // Then: 端点正确
        assertNotNull(endpoint);
        assertNotNull(endpoint.getKey());
        assertNotNull(endpoint.getSourceIp());
        assertNotNull(endpoint.getTargetIp());
    }

    @Test
    @DisplayName("场景: 多次创建的配置ID唯一")
    void testUniqueConfigIds() {
        // When: 创建多个配置
        TenantDeployConfig config1 = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        TenantDeployConfig config2 = TestDataFactory.createMinimalConfig("tenant2", 1001L);
        TenantDeployConfig config3 = TestDataFactory.createMinimalConfig("tenant3", 1001L);

        // Then: 租户ID都不同
        assertNotEquals(config1.getTenantId(), config2.getTenantId());
        assertNotEquals(config2.getTenantId(), config3.getTenantId());
        assertNotEquals(config1.getTenantId(), config3.getTenantId());
    }
}

