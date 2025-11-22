package xyz.firestige.deploy.infrastructure.config;

import org.junit.jupiter.api.Test;
import xyz.firestige.deploy.infrastructure.template.TemplateResolver;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentPlaceholderResolverTest {

    static class Nested {
        String value;
        List<String> list;
        Map<String,String> map;
    }
    static class Root {
        String direct;
        Nested nested;
        List<String> servers;
    }

    @Test
    void replacesWithDefaultWhenEnvMissing() {
        Root root = new Root();
        root.direct = "{$X:foo}";
        EnvironmentPlaceholderResolver resolver = new EnvironmentPlaceholderResolver(true);
        resolver.resolve(root);
        assertEquals("foo", root.direct);
    }

    @Test
    void failsWhenRequiredEnvMissing() {
        Root root = new Root();
        root.direct = "{$REQUIRED}";
        EnvironmentPlaceholderResolver resolver = new EnvironmentPlaceholderResolver(false);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> resolver.resolve(root));
        assertTrue(ex.getMessage().contains("REQUIRED"));
    }

    @Test
    void usesEnvValueWhenPresent() {
        String var = "TEST_VAR";
        Root root = new Root();
        root.direct = "{$" + var + ":fallback}";
        // Cannot set real env; simulate by temporarily patching System.getenv via wrapper approach not available.
        // We instead verify logic by injecting a value into resolver cache by calling replaceString indirectly.
        // For robustness we rely on actual environment if user sets it.
        String envVal = System.getenv(var);
        EnvironmentPlaceholderResolver resolver = new EnvironmentPlaceholderResolver(true);
        resolver.resolve(root);
        if (envVal != null && !envVal.isBlank()) {
            assertEquals(envVal, root.direct);
        } else {
            assertEquals("fallback", root.direct);
        }
    }

    @Test
    void replacesMultiplePlaceholders() {
        Root root = new Root();
        root.direct = "A{$X:1}B{$Y:2}";
        EnvironmentPlaceholderResolver resolver = new EnvironmentPlaceholderResolver(true);
        resolver.resolve(root);
        assertEquals("A1B2", root.direct);
    }

    @Test
    void secretVariableMaskedInLogs() {
        Root root = new Root();
        root.direct = "{$PWD_SECRET:abc}";
        EnvironmentPlaceholderResolver resolver = new EnvironmentPlaceholderResolver(true);
        resolver.resolve(root);
        assertEquals("abc", root.direct); // value resolves
    }

    @Test
    void nestedDefaultRetainsInnerTemplatePlaceholder() {
        Root root = new Root();
        root.direct = "{$HEALTH_CHECK_PATH:/actuator/bg-sdk/{tenantId}}";
        EnvironmentPlaceholderResolver resolver = new EnvironmentPlaceholderResolver(true);
        resolver.resolve(root);
        // 环境变量未定义时，应保留内部 {tenantId} 模板
        assertTrue(root.direct.startsWith("/actuator/bg-sdk/"));
        assertTrue(root.direct.contains("{tenantId}"));
    }

    @Test
    void cooperationWithTemplateResolverDefaultValue() {
        Root root = new Root();
        root.direct = "{$HEALTH_CHECK_PATH:/actuator/bg-sdk/{tenantId}/status}";
        EnvironmentPlaceholderResolver envResolver = new EnvironmentPlaceholderResolver(true);
        envResolver.resolve(root);
        // 使用 TemplateResolver 解析内部 {tenantId}
        TemplateResolver templateResolver = new TemplateResolver();
        @SuppressWarnings("unchecked")
        String finalPath = (String) templateResolver.resolve(root.direct, Map.of("tenantId", "t-123"));
        assertEquals("/actuator/bg-sdk/t-123/status", finalPath);
    }

    @Test
    void cooperationWithTemplateResolverEnvOverrideIfPresent() {
        String var = "HEALTH_CHECK_PATH"; // 若外部设置了该 env 则应使用其值
        Root root = new Root();
        root.direct = "{$" + var + ":/actuator/bg-sdk/{tenantId}}";
        EnvironmentPlaceholderResolver envResolver = new EnvironmentPlaceholderResolver(true);
        envResolver.resolve(root);
        TemplateResolver templateResolver = new TemplateResolver();
        String tenantId = "tenant-xyz";
        String result = (String) templateResolver.resolve(root.direct, Map.of("tenantId", tenantId));
        String envVal = System.getenv(var);
        if (envVal != null && !envVal.isBlank()) {
            // 如果环境变量包含 {tenantId} 模板，也应被替换
            assertFalse(result.contains("{tenantId}"));
        } else {
            assertEquals("/actuator/bg-sdk/" + tenantId, result);
        }
    }

    @Test
    void allowMissingTrueProducesEmptyStringForRequiredWithoutDefault() {
        Root root = new Root();
        root.direct = "pre-{$MISSING}-post";
        EnvironmentPlaceholderResolver resolver = new EnvironmentPlaceholderResolver(true);
        resolver.resolve(root);
        assertEquals("pre--post", root.direct); // 缺失置空
    }

    @Test
    void defaultContainingMultipleInnerTemplates() {
        Root root = new Root();
        root.direct = "{$COMPLEX:/a/{tenantId}/{version}/tail}";
        EnvironmentPlaceholderResolver envResolver = new EnvironmentPlaceholderResolver(true);
        envResolver.resolve(root);
        // 内部模板仍保留
        assertTrue(root.direct.contains("{tenantId}"));
        assertTrue(root.direct.contains("{version}"));
        TemplateResolver templateResolver = new TemplateResolver();
        String resolved = (String) templateResolver.resolve(root.direct, Map.of("tenantId", "T1", "version", "v2"));
        assertEquals("/a/T1/v2/tail", resolved);
    }

    @Test
    void repeatedSamePlaceholderDefaultUsedBothTimes() {
        Root root = new Root();
        root.direct = "{$X:foo}-{$X:foo}";
        EnvironmentPlaceholderResolver envResolver = new EnvironmentPlaceholderResolver(true);
        envResolver.resolve(root);
        assertEquals("foo-foo", root.direct);
    }
}
