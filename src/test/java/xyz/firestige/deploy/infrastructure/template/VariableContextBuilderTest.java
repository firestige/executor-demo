package xyz.firestige.deploy.infrastructure.template;

import org.junit.jupiter.api.Test;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 变量上下文构建器测试
 */
class VariableContextBuilderTest {

    private final VariableContextBuilder builder = new VariableContextBuilder();

    @Test
    void testBuildContext() {
        ServiceConfig serviceConfig = new TestServiceConfig(
            TenantId.of("tenant-001"),
            "blue-green-gateway"
        );

        Map<String, String> context = builder.buildContext(serviceConfig);

        assertEquals("tenant-001", context.get("tenantId"));
        assertEquals("blue-green-gateway", context.get("serviceType"));
        assertEquals(2, context.size());
    }

    @Test
    void testBuildContext_withDifferentTenant() {
        ServiceConfig serviceConfig = new TestServiceConfig(
            TenantId.of("tenant-999"),
            "portal"
        );

        Map<String, String> context = builder.buildContext(serviceConfig);

        assertEquals("tenant-999", context.get("tenantId"));
        assertEquals("portal", context.get("serviceType"));
    }

    // 测试用的简单 ServiceConfig 实现
    private static class TestServiceConfig implements ServiceConfig {
        private final TenantId tenantId;
        private final String serviceType;

        public TestServiceConfig(TenantId tenantId, String serviceType) {
            this.tenantId = tenantId;
            this.serviceType = serviceType;
        }

        @Override
        public TenantId getTenantId() {
            return tenantId;
        }

        @Override
        public String getServiceType() {
            return serviceType;
        }
    }
}

