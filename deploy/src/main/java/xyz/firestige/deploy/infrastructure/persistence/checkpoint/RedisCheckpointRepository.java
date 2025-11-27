package xyz.firestige.deploy.infrastructure.persistence.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.data.redis.core.StringRedisTemplate;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.CheckpointRepository;
import xyz.firestige.deploy.domain.task.TaskCheckpoint;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-backed CheckpointStore using RedisClient (Spring Data Redis underneath).
 */
public class RedisCheckpointRepository implements CheckpointRepository {

    private static final String DEFAULT_NAMESPACE = "executor:checkpoint:";
    private static final String DEFAULT_NAME = "repo";

    private final StringRedisTemplate redisTemplate;
    private final String key;
    private final Duration ttl;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // local read-through cache (optional small optimization)
    private final Map<TaskId, TaskCheckpoint> cache = new ConcurrentHashMap<>();

    public RedisCheckpointRepository(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_NAMESPACE + ":" + DEFAULT_NAME);
    }

    public RedisCheckpointRepository(StringRedisTemplate redisTemplate, String key) {
        this(redisTemplate, DEFAULT_NAMESPACE, key);
    }

    public RedisCheckpointRepository(StringRedisTemplate redisTemplate, String namespace, String name) {
        this(redisTemplate, namespace, name, Duration.ofDays(1));
    }

    public RedisCheckpointRepository(StringRedisTemplate redisTemplate, String namespace, String name, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.key = Objects.requireNonNullElse(namespace, DEFAULT_NAMESPACE) + ":" + Objects.requireNonNullElse(name, DEFAULT_NAME);
        this.ttl = ttl;
    }

    private String key() {
        return this.key;
    }

    @Override
    public void put(TaskId taskId, TaskCheckpoint checkpoint) {
        if (taskId == null || checkpoint == null) return;
        try {
            String jsonStr = mapper.writeValueAsString(checkpoint);
            redisTemplate.opsForHash().put(key(), taskId.getValue(), jsonStr);
            redisTemplate.expire(key, ttl);
            cache.put(taskId, checkpoint);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize checkpoint", e);
        }
    }

    @Override
    public TaskCheckpoint get(TaskId taskId) {
        if (taskId == null) {
            return null;
        }
        TaskCheckpoint cached = cache.get(taskId);
        if (cached != null) {
            return cached;
        }
        String data = redisTemplate.<String, String>opsForHash().get(key(), taskId.getValue());
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            TaskCheckpoint cp = mapper.readValue(data, TaskCheckpoint.class);
            cache.put(taskId, cp);
            return cp;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize checkpoint", e);
        }
    }

    @Override
    public void remove(TaskId taskId) {
        if (taskId == null) {
            return;
        }
        redisTemplate.opsForHash().delete(key(), taskId.getValue());
        cache.remove(taskId);
    }
}
