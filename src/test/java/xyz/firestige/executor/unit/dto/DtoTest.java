package xyz.firestige.executor.unit.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.entity.deploy.NetworkEndpoint;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.util.TimingExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DTO 类单元测试
 * 测试数据传输对象的基本功能
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("DTO 类单元测试")
class DtoTest {

    @Test
    @DisplayName("场景: TenantDeployConfig 基本属性")
    void testTenantDeployConfigBasicProperties() {
        // Given: 创建配置
        TenantDeployConfig config = new TenantDeployConfig();

        // When: 设置属性
        config.setTenantId("tenant123");
        config.setPlanId(1001L);

        // Then: 属性正确
        assertEquals("tenant123", config.getTenantId());
        assertEquals(1001L, config.getPlanId());
    }

    @Test
    @DisplayName("场景: TenantDeployConfig 网络端点列表")
    void testTenantDeployConfigNetworkEndpoints() {
        // Given: 配置和端点列表
        TenantDeployConfig config = new TenantDeployConfig();
        List<NetworkEndpoint> endpoints = new ArrayList<>();

        NetworkEndpoint endpoint1 = new NetworkEndpoint();
        endpoint1.setKey("endpoint1");
        endpoint1.setSourceIp("192.168.1.1");
        endpoints.add(endpoint1);

        NetworkEndpoint endpoint2 = new NetworkEndpoint();
        endpoint2.setKey("endpoint2");
        endpoint2.setSourceIp("192.168.1.2");
        endpoints.add(endpoint2);

        // When: 设置端点列表
        config.setNetworkEndpoints(endpoints);

        // Then: 列表正确
        assertNotNull(config.getNetworkEndpoints());
        assertEquals(2, config.getNetworkEndpoints().size());
        assertEquals("endpoint1", config.getNetworkEndpoints().get(0).getKey());
    }

    @Test
    @DisplayName("场景: NetworkEndpoint 基本属性")
    void testNetworkEndpointBasicProperties() {
        // Given: 创建端点
        NetworkEndpoint endpoint = new NetworkEndpoint();

        // When: 设置属性
        endpoint.setKey("ep1");
        endpoint.setSourceIp("10.0.0.1");
        endpoint.setSourceDomain("source.example.com");
        endpoint.setTargetIp("10.0.0.2");
        endpoint.setTargetDomain("target.example.com");

        // Then: 属性正确
        assertEquals("ep1", endpoint.getKey());
        assertEquals("10.0.0.1", endpoint.getSourceIp());
        assertEquals("source.example.com", endpoint.getSourceDomain());
        assertEquals("10.0.0.2", endpoint.getTargetIp());
        assertEquals("target.example.com", endpoint.getTargetDomain());
    }

    @Test
    @DisplayName("场景: NetworkEndpoint 支持只有域名")
    void testNetworkEndpointDomainOnly() {
        // Given: 只使用域名的端点
        NetworkEndpoint endpoint = new NetworkEndpoint();

        // When: 只设置域名
        endpoint.setKey("ep2");
        endpoint.setSourceDomain("source.example.com");
        endpoint.setTargetDomain("target.example.com");

        // Then: 域名正确，IP为null
        assertEquals("ep2", endpoint.getKey());
        assertNull(endpoint.getSourceIp());
        assertNull(endpoint.getTargetIp());
        assertEquals("source.example.com", endpoint.getSourceDomain());
        assertEquals("target.example.com", endpoint.getTargetDomain());
    }

    @Test
    @DisplayName("场景: NetworkEndpoint 支持只有IP")
    void testNetworkEndpointIpOnly() {
        // Given: 只使用IP的端点
        NetworkEndpoint endpoint = new NetworkEndpoint();

        // When: 只设置IP
        endpoint.setKey("ep3");
        endpoint.setSourceIp("192.168.1.1");
        endpoint.setTargetIp("192.168.1.2");

        // Then: IP正确，域名为null
        assertEquals("ep3", endpoint.getKey());
        assertEquals("192.168.1.1", endpoint.getSourceIp());
        assertEquals("192.168.1.2", endpoint.getTargetIp());
        assertNull(endpoint.getSourceDomain());
        assertNull(endpoint.getTargetDomain());
    }
}

