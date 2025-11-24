package xyz.firestige.infrastructure.redis.renewal.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RedisClient;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalResult;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AsyncRenewalExecutorTest {

    static class InMemoryRedisClient implements RedisClient {
        private final Set<String> store = Collections.synchronizedSet(new HashSet<>());
        private final Map<String, Long> ttlMap = new ConcurrentHashMap<>();
        private boolean failMode = false;
        InMemoryRedisClient(Collection<String> initial) { store.addAll(initial); }
        public void setFailMode(boolean fail) { this.failMode = fail; }
        @Override public boolean expire(String key, long ttlSeconds) { if(failMode) return false; if(!store.contains(key)) return false; ttlMap.put(key, ttlSeconds); return true; }
        @Override public Map<String, Boolean> batchExpire(Collection<String> keys, long ttlSeconds) { Map<String, Boolean> r=new LinkedHashMap<>(); for (String k: keys) { r.put(k, expire(k, ttlSeconds)); } return r; }
        @Override public CompletableFuture<Boolean> expireAsync(String key, long ttlSeconds) { return CompletableFuture.completedFuture(expire(key, ttlSeconds)); }
        @Override public CompletableFuture<Map<String, Boolean>> batchExpireAsync(Collection<String> keys, long ttlSeconds) { return CompletableFuture.completedFuture(batchExpire(keys, ttlSeconds)); }
        @Override public Collection<String> scan(String pattern, int count) { return store; }
        @Override public boolean exists(String key) { return store.contains(key); }
        @Override public long ttl(String key) { return ttlMap.getOrDefault(key, -2L); }
        @Override public void close() { }
    }

    private AsyncRenewalExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) executor.shutdown();
    }

    @Test
    void submit_success() throws ExecutionException, InterruptedException {
        InMemoryRedisClient client = new InMemoryRedisClient(List.of("k1","k2","k3"));
        executor = new AsyncRenewalExecutor(client, 2, 10);
        CompletableFuture<RenewalResult> f = executor.submit("t1", List.of("k1","k2"), 300);
        RenewalResult result = f.get(2, TimeUnit.SECONDS);
        assertTrue(result.isSuccess());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals(1, executor.getSubmittedTasks());
        assertEquals(1, executor.getCompletedTasks());
        assertTrue(client.ttl("k1") > 0);
    }

    @Test
    void submit_partial_failure() throws Exception {
        InMemoryRedisClient client = new InMemoryRedisClient(List.of("k1"));
        executor = new AsyncRenewalExecutor(client, 1, 5);
        RenewalResult r = executor.submit("t2", List.of("k1","missing"), 120).get(2, TimeUnit.SECONDS);
        assertEquals(1, r.getSuccessCount());
        assertEquals(1, r.getFailureCount());
    }

    @Test
    void submit_empty_keys() throws Exception {
        InMemoryRedisClient client = new InMemoryRedisClient(List.of());
        executor = new AsyncRenewalExecutor(client, 1, 5);
        RenewalResult r = executor.submit("t3", Collections.emptyList(), 100).get(2, TimeUnit.SECONDS);
        assertEquals(0, r.getSuccessCount());
        assertEquals(0, r.getFailureCount());
    }

    @Test
    void queue_backpressure_with_callerRunsPolicy() throws Exception {
        InMemoryRedisClient client = new InMemoryRedisClient(List.of("a","b","c","d","e"));
        executor = new AsyncRenewalExecutor(client, 1, 1); // small queue
        List<CompletableFuture<RenewalResult>> futures = new ArrayList<>();
        for (int i=0; i<5; i++) {
            futures.add(executor.submit("task"+i, List.of("a"), 50));
        }
        for (CompletableFuture<RenewalResult> f : futures) {
            assertTrue(f.get(2, TimeUnit.SECONDS).isSuccess());
        }
        assertEquals(5, executor.getSubmittedTasks());
        assertEquals(5, executor.getCompletedTasks());
    }
}

