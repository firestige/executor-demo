package xyz.firestige.deploy.infrastructure.persistence.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import xyz.firestige.deploy.domain.task.CheckpointRepository;
import xyz.firestige.deploy.domain.task.TaskCheckpoint;
import xyz.firestige.deploy.infrastructure.redis.RedisClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-backed CheckpointStore using RedisClient (Spring Data Redis underneath).
 */
public class RedisCheckpointRepository implements CheckpointRepository {

    private final RedisClient client;
    private final String namespace;
    private final Duration ttl;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // local read-through cache (optional small optimization)
    private final Map<String, TaskCheckpoint> cache = new ConcurrentHashMap<>();

    public RedisCheckpointRepository(RedisClient client, String namespace, Duration ttl) {
        this.client = client;
        this.namespace = (namespace == null || namespace.isBlank()) ? "executor:ckpt:" : namespace.endsWith(":") ? namespace : namespace + ":";
        this.ttl = ttl;
    }

    private String k(String taskId) { return namespace + taskId; }

    @Override
    public void put(String taskId, TaskCheckpoint checkpoint) {
        if (taskId == null || checkpoint == null) return;
        try {
            byte[] json = mapper.writeValueAsBytes(checkpoint);
            client.set(k(taskId), json, ttl);
            cache.put(taskId, checkpoint);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize checkpoint", e);
        }
    }

    @Override
    public TaskCheckpoint get(String taskId) {
        if (taskId == null) return null;
        TaskCheckpoint cached = cache.get(taskId);
        if (cached != null) return cached;
        byte[] data = client.get(k(taskId));
        if (data == null || data.length == 0) return null;
        try {
            TaskCheckpoint cp = mapper.readValue(data, TaskCheckpoint.class);
            cache.put(taskId, cp);
            return cp;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize checkpoint", e);
        }
    }

    @Override
    public void remove(String taskId) {
        if (taskId == null) return;
        client.del(k(taskId));
        cache.remove(taskId);
    }
}
