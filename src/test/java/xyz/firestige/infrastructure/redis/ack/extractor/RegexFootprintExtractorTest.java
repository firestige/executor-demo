package xyz.firestige.infrastructure.redis.ack.extractor;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.ack.exception.FootprintExtractionException;

import static org.junit.jupiter.api.Assertions.*;

class RegexFootprintExtractorTest {

    @Test
    void extract_singleGroup_success() {
        RegexFootprintExtractor extractor = new RegexFootprintExtractor("version=(v\\d+\\.\\d+\\.\\d+)");
        String input = "payload: version=v1.2.3; status=ok";
        assertEquals("v1.2.3", extractor.extract(input));
    }

    @Test
    void extract_multiGroup_success() {
        RegexFootprintExtractor extractor = new RegexFootprintExtractor("(v\\d+)\\.(\\d+)\\.(\\d+)", 1);
        String input = "release=v2.10.5";
        assertEquals("v2", extractor.extract(input));
    }

    @Test
    void extract_notMatched_throws() {
        RegexFootprintExtractor extractor = new RegexFootprintExtractor("version=(v\\d+)");
        assertThrows(FootprintExtractionException.class, () -> extractor.extract("no version here"));
    }

    @Test
    void extract_groupOutOfRange_throws() {
        RegexFootprintExtractor extractor = new RegexFootprintExtractor("version=(v\\d+)", 2);
        assertThrows(FootprintExtractionException.class, () -> extractor.extract("version=v3"));
    }
}

