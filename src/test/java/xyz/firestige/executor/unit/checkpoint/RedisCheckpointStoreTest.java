package xyz.firestige.executor.unit.checkpoint;

import org.junit.jupiter.api.Test;
import xyz.firestige.executor.checkpoint.RedisCheckpointStore;
import xyz.firestige.executor.domain.task.TaskCheckpoint;
import xyz.firestige.executor.redis.RedisClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RedisCheckpointStoreTest {

    static class FakeRedis implements RedisClient {
        private final Map<String, byte[]> m = new HashMap<>();
        @Override public void set(String key, byte[] value, Duration ttl) { m.put(key, value); }
        @Override public byte[] get(String key) { return m.get(key); }
        @Override public void del(String key) { m.remove(key); }
    }

    @Test
    void putGetRemoveWorks() {
        RedisClient client = new FakeRedis();
        RedisCheckpointStore store = new RedisCheckpointStore(client, "ut:ckpt", Duration.ofMinutes(30));
        TaskCheckpoint cp = new TaskCheckpoint();
        cp.setLastCompletedStageIndex(2);
        store.put("tid", cp);
        TaskCheckpoint got = store.get("tid");
        assertNotNull(got);
        assertEquals(2, got.getLastCompletedStageIndex());
        store.remove("tid");
        assertNull(store.get("tid"));
    }
}

