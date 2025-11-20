package xyz.firestige.deploy.infrastructure.template;

import org.junit.jupiter.api.Test;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模板解析器测试
 */
class TemplateResolverTest {

    private final TemplateResolver resolver = new TemplateResolver();

    @Test
    void testResolveString_withSinglePlaceholder() {
        String template = "/actuator/bg-sdk/{tenantId}";
        Map<String, String> variables = Map.of("tenantId", "tenant-001");

        String result = (String) resolver.resolve(template, variables);

        assertEquals("/actuator/bg-sdk/tenant-001", result);
    }

    @Test
    void testResolveString_withMultiplePlaceholders() {
        String template = "/api/{version}/tenants/{tenantId}/config";
        Map<String, String> variables = Map.of(
            "version", "v1",
            "tenantId", "tenant-001"
        );

        String result = (String) resolver.resolve(template, variables);

        assertEquals("/api/v1/tenants/tenant-001/config", result);
    }

    @Test
    void testResolveString_jsonTemplate() {
        String template = "{\"tenantId\":\"{tenantId}\",\"appName\":\"icc-bg-gateway\"}";
        Map<String, String> variables = Map.of("tenantId", "tenant-001");

        String result = (String) resolver.resolve(template, variables);

        assertEquals("{\"tenantId\":\"tenant-001\",\"appName\":\"icc-bg-gateway\"}", result);
    }

    @Test
    void testResolveString_noPlaceholders() {
        String template = "/actuator/health";
        Map<String, String> variables = Map.of("tenantId", "tenant-001");

        String result = (String) resolver.resolve(template, variables);

        assertEquals("/actuator/health", result);
    }

    @Test
    void testResolveString_missingVariable() {
        String template = "/actuator/{tenantId}/health";
        Map<String, String> variables = Map.of("otherId", "123");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            resolver.resolve(template, variables);
        });

        assertTrue(exception.getMessage().contains("Missing variable value for placeholder"));
        assertTrue(exception.getMessage().contains("{tenantId}"));
    }

    @Test
    void testResolveMap() {
        Map<String, Object> config = new HashMap<>();
        config.put("message", "{\"tenantId\":\"{tenantId}\"}");
        config.put("path", "/api/{tenantId}");
        config.put("timeout", 3000);

        Map<String, String> variables = Map.of("tenantId", "tenant-001");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resolver.resolve(config, variables);

        assertEquals("{\"tenantId\":\"tenant-001\"}", result.get("message"));
        assertEquals("/api/tenant-001", result.get("path"));
        assertEquals(3000, result.get("timeout"));
    }

    @Test
    void testResolveList() {
        List<Object> list = List.of(
            "/api/{tenantId}/config",
            "/api/{tenantId}/status",
            123
        );

        Map<String, String> variables = Map.of("tenantId", "tenant-001");

        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) resolver.resolve(list, variables);

        assertEquals("/api/tenant-001/config", result.get(0));
        assertEquals("/api/tenant-001/status", result.get(1));
        assertEquals(123, result.get(2));
    }

    @Test
    void testResolveNestedMap() {
        Map<String, Object> config = new HashMap<>();
        config.put("message", "{\"tenantId\":\"{tenantId}\"}");

        Map<String, Object> retryPolicy = new HashMap<>();
        retryPolicy.put("endpoint", "/health/{tenantId}");
        retryPolicy.put("maxAttempts", 3);

        config.put("retry-policy", retryPolicy);

        Map<String, String> variables = Map.of("tenantId", "tenant-001");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resolver.resolve(config, variables);

        assertEquals("{\"tenantId\":\"tenant-001\"}", result.get("message"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultRetryPolicy = (Map<String, Object>) result.get("retry-policy");
        assertEquals("/health/tenant-001", resultRetryPolicy.get("endpoint"));
        assertEquals(3, resultRetryPolicy.get("maxAttempts"));
    }

    @Test
    void testResolveNull() {
        Map<String, String> variables = Map.of("tenantId", "tenant-001");
        Object result = resolver.resolve(null, variables);
        assertNull(result);
    }
}

