package xyz.firestige.infrastructure.redis.ack.extractor;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.ack.exception.FootprintExtractionException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonFieldExtractorTest {

    @Test
    void extract_fromMap_success() {
        JsonFieldExtractor extractor = new JsonFieldExtractor("version");
        Map<String, Object> value = Map.of("version", "v1.0.0", "config", "data");

        String result = extractor.extract(value);

        assertEquals("v1.0.0", result);
    }

    @Test
    void extract_fromJsonString_success() {
        JsonFieldExtractor extractor = new JsonFieldExtractor("version");
        String json = "{\"version\":\"v2.0.0\",\"data\":\"test\"}";

        String result = extractor.extract(json);

        assertEquals("v2.0.0", result);
    }

    @Test
    void extract_fieldNotFound_throwsException() {
        JsonFieldExtractor extractor = new JsonFieldExtractor("missing");
        Map<String, Object> value = Map.of("version", "v1.0.0");

        assertThrows(FootprintExtractionException.class, () -> extractor.extract(value));
    }

    @Test
    void extract_nullField_throwsException() {
        JsonFieldExtractor extractor = new JsonFieldExtractor("version");
        Map<String, Object> value = new java.util.HashMap<>();
        value.put("version", null);
        value.put("config", "data");

        assertThrows(FootprintExtractionException.class, () -> extractor.extract(value));
    }
}

