package xyz.firestige.infrastructure.redis.renewal.keygen;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderKeyGeneratorTest {

    @Test
    void generateKey_noPlaceholders_returnsOriginal() {
        PlaceholderKeyGenerator generator = new PlaceholderKeyGenerator();
        String result = generator.generateKey("simple:key", Map.of());
        assertEquals("simple:key", result);
    }

    @Test
    void generateKey_singlePlaceholder() {
        PlaceholderKeyGenerator generator = new PlaceholderKeyGenerator();
        String result = generator.generateKey("task:{tenantId}:config", Map.of("tenantId", "001"));
        assertEquals("task:001:config", result);
    }

    @Test
    void generateKey_multiplePlaceholders() {
        PlaceholderKeyGenerator generator = new PlaceholderKeyGenerator();
        Map<String, Object> context = Map.of("tenantId", "001", "taskId", "123", "version", 2);
        String result = generator.generateKey("task:{tenantId}:{taskId}:v{version}", context);
        assertEquals("task:001:123:v2", result);
    }

    @Test
    void generateKey_missingValue_replacesWithEmpty() {
        PlaceholderKeyGenerator generator = new PlaceholderKeyGenerator();
        String result = generator.generateKey("task:{missing}:config", Map.of("other", "value"));
        assertEquals("task::config", result);
    }
}

