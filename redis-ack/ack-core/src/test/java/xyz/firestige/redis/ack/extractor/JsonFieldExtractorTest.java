package xyz.firestige.redis.ack.extractor;

import org.junit.jupiter.api.Test;
import xyz.firestige.redis.ack.api.FootprintExtractor;
import xyz.firestige.redis.ack.exception.FootprintExtractionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class JsonFieldExtractorTest {

    @Test
    void extract() {
        FootprintExtractor extractor = new JsonFieldExtractor("version");
        String jsonStr = "{\"version\":\"1.0.0\",\"name\":\"test-app\"}";
        try {
            String result = extractor.extract(jsonStr);
            assertEquals("1.0.0", result);
        } catch (Exception e) {
            fail("Extraction failed: " + e.getMessage());
        }
    }

    @Test
    void extractNestedFieldWithJsonPath() {
        // 测试 JSONPath 风格的嵌套字段提取
        FootprintExtractor extractor = new JsonFieldExtractor("$.metadata.version");
        String jsonStr = "{\"metadata\":{\"version\":\"2.0.0\",\"author\":\"test\"},\"name\":\"test-app\"}";
        try {
            String result = extractor.extract(jsonStr);
            assertEquals("2.0.0", result);
        } catch (Exception e) {
            fail("Extraction failed: " + e.getMessage());
        }
    }

    @Test
    void extractDeeplyNestedField() {
        // 测试多层嵌套字段提取
        FootprintExtractor extractor = new JsonFieldExtractor("$.data.config.app.version");
        String jsonStr = "{\"data\":{\"config\":{\"app\":{\"version\":\"3.0.0\",\"env\":\"prod\"}}}}";
        try {
            String result = extractor.extract(jsonStr);
            assertEquals("3.0.0", result);
        } catch (Exception e) {
            fail("Extraction failed: " + e.getMessage());
        }
    }

    @Test
    void extractFromNestedMap() {
        // 测试从嵌套 Map 中提取
        FootprintExtractor extractor = new JsonFieldExtractor("$.user.profile.userId");
        String jsonStr = "{\"user\":{\"profile\":{\"userId\":\"12345\",\"name\":\"John\"}}}";
        try {
            String result = extractor.extract(jsonStr);
            assertEquals("12345", result);
        } catch (Exception e) {
            fail("Extraction failed: " + e.getMessage());
        }
    }

    @Test
    void extractNonExistentNestedField() {
        // 测试不存在的嵌套字段
        FootprintExtractor extractor = new JsonFieldExtractor("$.metadata.nonexistent");
        String jsonStr = "{\"metadata\":{\"version\":\"2.0.0\"},\"name\":\"test-app\"}";

        assertThrows(FootprintExtractionException.class, () -> {
            extractor.extract(jsonStr);
        });
    }

    @Test
    void extractWithSimpleDotNotation() {
        // 测试不带 $. 前缀的点分隔路径（兼容性）
        FootprintExtractor extractor = new JsonFieldExtractor("metadata.version");
        String jsonStr = "{\"metadata\":{\"version\":\"4.0.0\"},\"name\":\"test-app\"}";
        try {
            String result = extractor.extract(jsonStr);
            assertEquals("4.0.0", result);
        } catch (Exception e) {
            fail("Extraction failed: " + e.getMessage());
        }
    }

    @Test
    void extractFromMultipleVersionFields() {
        // 测试当有多个 version 字段时，能正确提取指定路径的 version
        FootprintExtractor extractor1 = new JsonFieldExtractor("$.app.version");
        FootprintExtractor extractor2 = new JsonFieldExtractor("$.lib.version");

        String jsonStr = "{\"app\":{\"version\":\"1.0.0\"},\"lib\":{\"version\":\"2.0.0\"}}";

        try {
            String result1 = extractor1.extract(jsonStr);
            String result2 = extractor2.extract(jsonStr);

            assertEquals("1.0.0", result1);
            assertEquals("2.0.0", result2);
        } catch (Exception e) {
            fail("Extraction failed: " + e.getMessage());
        }
    }
}

