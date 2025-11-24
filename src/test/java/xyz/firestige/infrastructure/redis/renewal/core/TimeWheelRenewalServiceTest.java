package xyz.firestige.infrastructure.redis.renewal.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TimeWheelRenewalServiceTest {

    // 简单内存 RedisClient
    static class MemClient implements RedisClient {
        private final Set<String> keys = ConcurrentHashMap.newKeySet();
        MemClient(Collection<String> init){ keys.addAll(init);}
        @Override public boolean expire(String key, long ttl){ return keys.contains(key);}
        @Override public Map<String, Boolean> batchExpire(Collection<String> ks,long ttl){ Map<String,Boolean> r=new LinkedHashMap<>(); for(String k:ks) r.put(k, expire(k,ttl)); return r; }
        @Override public CompletableFuture<Boolean> expireAsync(String key,long ttl){ return CompletableFuture.completedFuture(expire(key,ttl)); }
        @Override public CompletableFuture<Map<String, Boolean>> batchExpireAsync(Collection<String> ks,long ttl){ return CompletableFuture.completedFuture(batchExpire(ks,ttl)); }
        @Override public Collection<String> scan(String pattern,int count){ return keys; }
        @Override public boolean exists(String key){ return keys.contains(key); }
        @Override public long ttl(String key){ return 100; }
        @Override public void close(){}
    }

    // 固定策略实现
    static class FixedTtl implements RenewalStrategy {
        private final Duration ttl; FixedTtl(Duration ttl){this.ttl=ttl;}
        @Override public Duration calculateTtl(RenewalContext context){ return ttl; }
        @Override public boolean shouldContinue(RenewalContext context){ return true; }
        @Override public String getName(){ return "FixedTtl"; }
    }
    static class FixedInterval implements RenewalIntervalStrategy {
        private final Duration interval; FixedInterval(Duration d){ interval=d; }
        @Override public Duration calculateInterval(RenewalContext context){ return interval; }
        @Override public String getName(){ return "FixedInterval"; }
    }
    static class StaticSelector implements KeySelector {
        private final Collection<String> keys; StaticSelector(Collection<String> k){keys=k;}
        @Override public Collection<String> selectKeys(RenewalContext context){ return keys; }
        @Override public String getName(){ return "StaticSelector"; }
    }
    static class CountStop implements StopCondition {
        private final long max; CountStop(long m){ max=m; }
        @Override public boolean shouldStop(RenewalContext context){ return context.getRenewalCount() >= max; }
        @Override public String getName(){ return "CountStop"; }
    }

    private TimeWheelRenewalService service;

    @AfterEach void tearDown(){ if(service!=null) service.shutdown(); }

    private RenewalTask buildTask(Collection<String> keys, long ttlSec, long intervalMs, long maxCount){
        return RenewalTask.builder()
                .keySelector(new StaticSelector(keys))
                .ttlStrategy(new FixedTtl(Duration.ofSeconds(ttlSec)))
                .intervalStrategy(new FixedInterval(Duration.ofMillis(intervalMs)))
                .stopCondition(new CountStop(maxCount))
                .build();
    }

    @Test
    void register_and_auto_complete_by_stop_condition() throws Exception {
        AsyncRenewalExecutor exec = new AsyncRenewalExecutor(new MemClient(List.of("k1")), 2, 10);
        service = new TimeWheelRenewalService(exec, 50, 256);
        String id = service.register(buildTask(List.of("k1"), 5, 100, 3));
        // 等待调度几次
        TimeUnit.MILLISECONDS.sleep(600);
        RenewalTaskStatus status = service.getStatus(id);
        assertNull(status, "应已完成并从状态中移除");
    }

    @Test
    void pause_and_resume() throws Exception {
        AsyncRenewalExecutor exec = new AsyncRenewalExecutor(new MemClient(List.of("k1")), 2, 10);
        service = new TimeWheelRenewalService(exec, 50, 256);
        String id = service.register(buildTask(List.of("k1"), 5, 80, 5));
        TimeUnit.MILLISECONDS.sleep(120); // 至少发生一次续期
        service.pause(id);
        RenewalTaskStatus pausedStatus = service.getStatus(id);
        assertEquals(RenewalTaskStatus.State.PAUSED, pausedStatus.getState());
        long countAfterPause = pausedStatus.getRenewalCount();
        TimeUnit.MILLISECONDS.sleep(200); // 暂停期间不增加
        assertEquals(countAfterPause, service.getStatus(id).getRenewalCount());
        service.resume(id);
        TimeUnit.MILLISECONDS.sleep(200);
        assertTrue(service.getStatus(id).getRenewalCount() > countAfterPause);
    }

    @Test
    void cancel_task() throws Exception {
        AsyncRenewalExecutor exec = new AsyncRenewalExecutor(new MemClient(List.of("k1")), 2, 10);
        service = new TimeWheelRenewalService(exec, 50, 256);
        String id = service.register(buildTask(List.of("k1"), 5, 100, 50));
        TimeUnit.MILLISECONDS.sleep(100);
        service.cancel(id);
        assertNull(service.getStatus(id));
    }

    @Test
    void multiple_tasks_independent() throws Exception {
        AsyncRenewalExecutor exec = new AsyncRenewalExecutor(new MemClient(List.of("a","b")), 2, 10);
        service = new TimeWheelRenewalService(exec, 30, 128);
        String id1 = service.register(buildTask(List.of("a"), 5, 60, 4));
        String id2 = service.register(buildTask(List.of("b"), 5, 90, 4));
        TimeUnit.MILLISECONDS.sleep(500);
        assertNull(service.getStatus(id1));
        assertNull(service.getStatus(id2));
    }
}

