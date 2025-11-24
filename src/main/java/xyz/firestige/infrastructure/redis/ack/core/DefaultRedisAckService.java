package xyz.firestige.infrastructure.redis.ack.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.infrastructure.redis.ack.api.RedisAckService;
import xyz.firestige.infrastructure.redis.ack.api.WriteStageBuilder;

/**
 * Redis ACK 服务默认实现
 *
 * @author AI
 * @since 1.0
 */
public class DefaultRedisAckService implements RedisAckService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DefaultRedisAckService(RedisTemplate<String, String> redisTemplate,
                                  RestTemplate restTemplate,
                                  ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public WriteStageBuilder write() {
        return new WriteStageBuilderImpl(redisTemplate, restTemplate, objectMapper);
    }
}

