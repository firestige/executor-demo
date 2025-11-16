package xyz.firestige.executor.unit.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.entity.deploy.NetworkEndpoint;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.util.TimingExtension;
import xyz.firestige.executor.validation.ValidationResult;
import xyz.firestige.executor.validation.validator.NetworkEndpointValidator;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NetworkEndpointValidator 单元测试
 * 测试网络端点校验逻辑
 *
 * 预计执行时间：20 秒（4 个测试）
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("NetworkEndpointValidator 单元测试")
class NetworkEndpointValidatorTest {

    private final NetworkEndpointValidator validator = new NetworkEndpointValidator();

    @Test
    @DisplayName("场景 2.1.5: 网络端点列表为空 - 校验失败")
    void testNetworkEndpointsEmpty() {
        // Given: networkEndpoints 为空
        TenantDeployConfig config = new TenantDeployConfig();
        config.setTenantId("tenant1");
        config.setNetworkEndpoints(new ArrayList<>());

        // When: 执行校验
        ValidationResult result = validator.validate(config);

        // Then: 校验失败
        assertFalse(result.isValid(), "空的网络端点列表应该校验失败");
        assertTrue(result.hasErrors(), "应该有错误信息");
        assertTrue(result.getErrors().get(0).getMessage().contains("不能为空"),
                "错误消息应该说明不能为空");
    }

    @Test
    @DisplayName("场景 2.1.6: IP 地址格式错误 - 校验失败")
    void testInvalidIpAddress() {
        // Given: sourceIp 格式错误
        TenantDeployConfig config = new TenantDeployConfig();
        config.setTenantId("tenant1");

        NetworkEndpoint endpoint = new NetworkEndpoint();
        endpoint.setKey("endpoint1");  // 必需字段
        endpoint.setSourceIp("999.999.999.999");  // 非法 IP
        endpoint.setSourceDomain("service.example.com");
        endpoint.setTargetIp("10.0.0.1");  // 必需的目标地址

        config.setNetworkEndpoints(List.of(endpoint));

        // When: 执行校验
        ValidationResult result = validator.validate(config);

        // Then: 校验失败
        assertFalse(result.isValid(), "非法 IP 地址应该校验失败");
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).getMessage().contains("IP") ||
                   result.getErrors().get(0).getMessage().contains("格式"),
                "错误消息应该提到 IP 或格式");
    }

    @Test
    @DisplayName("场景 2.1.7: IP 地址格式正确 - 校验通过")
    void testValidIpAddress() {
        // Given: 合法的配置
        TenantDeployConfig config = new TenantDeployConfig();
        config.setTenantId("tenant1");

        NetworkEndpoint endpoint = new NetworkEndpoint();
        endpoint.setKey("endpoint1");  // 必需字段
        endpoint.setSourceIp("192.168.1.1");  // 合法 IP
        endpoint.setSourceDomain("service.example.com");
        endpoint.setTargetIp("10.0.0.1");  // 必需的目标地址
        endpoint.setTargetDomain("target.example.com");

        config.setNetworkEndpoints(List.of(endpoint));

        // When: 执行校验
        ValidationResult result = validator.validate(config);

        // Then: 校验通过
        assertTrue(result.isValid(), "合法配置应该校验通过");
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("场景 2.1.8: 缺少源地址 - 校验失败")
    void testMissingSourceAddress() {
        // Given: 既没有 sourceIp 也没有 sourceDomain
        TenantDeployConfig config = new TenantDeployConfig();
        config.setTenantId("tenant1");

        NetworkEndpoint endpoint = new NetworkEndpoint();
        endpoint.setKey("endpoint1");  // 必需字段
        // sourceIp 和 sourceDomain 都为 null
        endpoint.setTargetIp("10.0.0.1");  // 提供目标地址

        config.setNetworkEndpoints(List.of(endpoint));

        // When: 执行校验
        ValidationResult result = validator.validate(config);

        // Then: 校验失败
        assertFalse(result.isValid(), "缺少源地址应该校验失败");
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("源地址") || e.getMessage().contains("source")),
                "错误消息应该说明缺少源地址");
    }
}

