package xyz.firestige.infrastructure.redis.renewal.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.infrastructure.redis.renewal.api.RedisClient;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalResult;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步续期执行器
 * <p>负责在独立线程池中执行 Redis 续期，避免阻塞调度线程。
 */
public class AsyncRenewalExecutor {
    private static final Logger log = LoggerFactory.getLogger(AsyncRenewalExecutor.class);

    private final RedisClient redisClient;
    private final ExecutorService pool;
    private final int queueCapacity;
    private final AtomicLong submittedTasks = new AtomicLong();
    private final AtomicLong completedTasks = new AtomicLong();

    public AsyncRenewalExecutor(RedisClient redisClient, int threadPoolSize, int queueCapacity) {
        this.redisClient = Objects.requireNonNull(redisClient);
        this.queueCapacity = queueCapacity;
        this.pool = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadFactory() {
                    private final AtomicLong idx = new AtomicLong();
                    @Override public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "redis-renewal-exec-" + idx.incrementAndGet());
                        t.setDaemon(true); return t; }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 降级策略：调用线程执行
        );
    }

    /**
     * 提交续期任务（异步）。
     * @param taskId 任务ID
     * @param keys 要续期的 key 集合
     * @param ttlSeconds TTL 秒
     */
    public CompletableFuture<RenewalResult> submit(String taskId, Collection<String> keys, long ttlSeconds) {
        submittedTasks.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> doRenew(taskId, keys, ttlSeconds), pool)
                .whenComplete((r,e) -> completedTasks.incrementAndGet());
    }

    private RenewalResult doRenew(String taskId, Collection<String> keys, long ttlSeconds) {
        long start = System.nanoTime();
        if (keys == null || keys.isEmpty()) {
            return RenewalResult.success(taskId, 0, 0, 0);
        }
        Map<String, Boolean> results = redisClient.batchExpire(keys, ttlSeconds);
        long success = results.values().stream().filter(Boolean::booleanValue).count();
        long failure = results.size() - success;
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        if (failure > 0) {
            log.warn("续期部分失败: taskId={}, success={}, failure={}", taskId, success, failure);
        } else {
            log.debug("续期完成: taskId={}, count={}, duration={}ms", taskId, success, durationMs);
        }
        return RenewalResult.success(taskId, success, failure, durationMs);
    }

    public long getSubmittedTasks() { return submittedTasks.get(); }
    public long getCompletedTasks() { return completedTasks.get(); }
    public int getActiveThreads() { return ((ThreadPoolExecutor) pool).getActiveCount(); }
    public int getQueueSize() { return ((ThreadPoolExecutor) pool).getQueue().size(); }
    public int getQueueRemainingCapacity() { return ((ThreadPoolExecutor) pool).getQueue().remainingCapacity(); }

    public void shutdown() {
        pool.shutdown();
    }
}

