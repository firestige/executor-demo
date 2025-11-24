package xyz.firestige.infrastructure.redis.ack.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.infrastructure.redis.ack.api.AckResult;
import xyz.firestige.infrastructure.redis.ack.api.RedisAckService;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redis ACK 服务集成测试
 *
 * @author AI
 * @since 1.0
 */
class RedisAckServiceIntegrationTest {

    private RedisTemplate<String, String> redisTemplate;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private RedisAckService ackService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();

        // Mock Redis operations
        when(redisTemplate.opsForValue()).thenReturn(mock());
        when(redisTemplate.opsForHash()).thenReturn(mock());

        ackService = new DefaultRedisAckService(redisTemplate, restTemplate, objectMapper);
    }

    @Test
    void write_buildsWriteStage() {
        WriteStageBuilderImpl builder = (WriteStageBuilderImpl) ackService.write();

        assertNotNull(builder);
    }

    @Test
    void buildFlow_allStages_success() {
        // 测试完整的流程构建（不执行）
        assertDoesNotThrow(() -> {
            ackService.write()
                .key("test-key")
                .value(Map.of("version", "v1.0.0"))
                .footprint("version")
                .andPublish()
                    .topic("test-topic")
                    .message("TEST_MESSAGE")
                .andVerify()
                    .httpGet("http://localhost/test")
                    .extractJson("version")
                    .retryFixedDelay(3, Duration.ofSeconds(1));
        });
    }

    @Test
    void write_missingKey_throwsException() {
        assertThrows(IllegalStateException.class, () -> {
            ackService.write()
                .value(Map.of("version", "v1.0.0"))
                .footprint("version")
                .andPublish();
        });
    }

    @Test
    void write_missingValue_throwsException() {
        assertThrows(IllegalStateException.class, () -> {
            ackService.write()
                .key("test-key")
                .footprint("version")
                .andPublish();
        });
    }

    @Test
    void write_missingFootprint_throwsException() {
        assertThrows(IllegalStateException.class, () -> {
            ackService.write()
                .key("test-key")
                .value(Map.of("version", "v1.0.0"))
                .andPublish();
        });
    }

    @Test
    void publish_missingTopic_throwsException() {
        assertThrows(IllegalStateException.class, () -> {
            ackService.write()
                .key("test-key")
                .value(Map.of("version", "v1.0.0"))
                .footprint("version")
                .andPublish()
                    .message("TEST")
                    .andVerify();
        });
    }

    @Test
    void verify_missingEndpoint_throwsException() {
        assertThrows(IllegalStateException.class, () -> {
            ackService.write()
                .key("test-key")
                .value(Map.of("version", "v1.0.0"))
                .footprint("version")
                .andPublish()
                    .topic("test-topic")
                    .message("TEST")
                .andVerify()
                    .extractJson("version")
                    .retryFixedDelay(3, Duration.ofSeconds(1))
                    .executeAndWait();
        });
    }
}

