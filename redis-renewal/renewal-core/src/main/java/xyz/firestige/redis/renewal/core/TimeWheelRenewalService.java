package xyz.firestige.redis.renewal.core;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.infrastructure.redis.renewal.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于时间轮的续期服务实现（调度层，不做IO）。
 */
public class TimeWheelRenewalService implements KeyRenewalService {
    private static final Logger log = LoggerFactory.getLogger(TimeWheelRenewalService.class);

    private final HashedWheelTimer timer;
    private final AsyncRenewalExecutor executor;
    private final Map<String, RenewalTaskWrapper> tasks = new ConcurrentHashMap<>();
    private volatile boolean shutdown = false;

    public TimeWheelRenewalService(AsyncRenewalExecutor executor, long tickMs, int wheelSize) {
        this.executor = Objects.requireNonNull(executor);
        this.timer = new HashedWheelTimer(
                r -> { Thread t = new Thread(r, "renewal-wheel"); t.setDaemon(true); return t; },
                tickMs, TimeUnit.MILLISECONDS, wheelSize
        );
    }

    @Override
    public String register(RenewalTask task) {
        if (shutdown) throw new IllegalStateException("service shutdown");
        String id = UUID.randomUUID().toString();
        RenewalContext ctx = new RenewalContext(id);
        RenewalTaskWrapper wrapper = new RenewalTaskWrapper(id, task, ctx);
        tasks.put(id, wrapper);
        schedule(wrapper, initialDelay(task));
        if (task.getListener() != null) task.getListener().onTaskRegistered(id, task);
        log.info("注册续期任务: id={}, initialDelay={}ms", id, initialDelay(task));
        return id;
    }

    private long initialDelay(RenewalTask task) {
        Duration interval = task.getIntervalStrategy().calculateInterval(new RenewalContext("dry-run"));
        return Math.max(interval.toMillis(), 10); // 最小 10ms 保护
    }

    private void schedule(RenewalTaskWrapper wrapper, long delayMs) {
        timer.newTimeout(new TimerTask() {
            @Override public void run(Timeout timeout) { onTick(wrapper); }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void onTick(RenewalTaskWrapper wrapper) {
        if (shutdown) return;
        RenewalTask task = wrapper.task();
        RenewalContext ctx = wrapper.context();
        // 如果暂停：仍然重新调度（保持任务活跃可恢复）
        if (wrapper.paused()) {
            Duration pauseInterval = task.getIntervalStrategy().calculateInterval(ctx);
            schedule(wrapper, Math.max(pauseInterval.toMillis(), 10));
            return;
        }
        // 获取 Key 集合并计算 TTL
        Collection<String> keys = task.getKeySelector().selectKeys(ctx);
        Duration ttl = task.getTtlStrategy().calculateTtl(ctx);
        long ttlSeconds = Math.max(ttl.getSeconds(), 1);

        // 记录 TTL 供自适应策略使用
        ctx.setLastCalculatedTtl(ttl);

        executor.submit(wrapper.id(), keys, ttlSeconds)
                .whenComplete((result, error) -> {
                    // 更新上下文
                    ctx.setLastRenewalTime(Instant.now());
                    ctx.incrementRenewalCount();
                    if (result != null) {
                        ctx.addSuccessCount(result.getSuccessCount());
                        ctx.addFailureCount(result.getFailureCount());
                        if (task.getListener() != null) task.getListener().afterRenewal(wrapper.id(), result);
                    }
                    if (error != null) {
                        log.error("续期执行异常: id={}", wrapper.id(), error);
                        if (task.getListener() != null) task.getListener().onTaskFailed(wrapper.id(), error);
                    }
                    // 停止条件与策略继续检查（在次数更新后）
                    boolean stopByCondition = task.getStopCondition() != null && task.getStopCondition().shouldStop(ctx);
                    boolean continueByStrategy = task.getTtlStrategy().shouldContinue(ctx);
                    if (stopByCondition || !continueByStrategy) {
                        complete(wrapper, stopByCondition ? RenewalLifecycleListener.CompletionReason.STOP_CONDITION_MET : RenewalLifecycleListener.CompletionReason.CANCELLED);
                        return;
                    }
                    // 安排下次调度
                    Duration nextInterval = task.getIntervalStrategy().calculateInterval(ctx);
                    schedule(wrapper, Math.max(nextInterval.toMillis(), 10));
                });
    }

    private void complete(RenewalTaskWrapper wrapper, RenewalLifecycleListener.CompletionReason reason) {
        tasks.remove(wrapper.id());
        if (wrapper.task().getListener() != null) wrapper.task().getListener().onTaskCompleted(wrapper.id(), reason);
        log.info("续期任务完成: id={}, reason={}", wrapper.id(), reason);
    }

    @Override
    public void cancel(String taskId) {
        RenewalTaskWrapper w = tasks.remove(taskId);
        if (w != null) {
            complete(w, RenewalLifecycleListener.CompletionReason.CANCELLED);
        }
    }

    @Override
    public void pause(String taskId) {
        RenewalTaskWrapper w = tasks.get(taskId);
        if (w != null) w.pause();
    }

    @Override
    public void resume(String taskId) {
        RenewalTaskWrapper w = tasks.get(taskId);
        if (w != null) w.resume();
    }

    @Override
    public RenewalTaskStatus getStatus(String taskId) {
        RenewalTaskWrapper w = tasks.get(taskId);
        if (w == null) return null;
        return new RenewalTaskStatus(taskId, w.paused() ? RenewalTaskStatus.State.PAUSED : RenewalTaskStatus.State.RUNNING, w.context());
    }

    @Override
    public Collection<RenewalTask> getAllTasks() {
        List<RenewalTask> list = new ArrayList<>();
        for (RenewalTaskWrapper w : tasks.values()) list.add(w.task());
        return Collections.unmodifiableList(list);
    }

    public void shutdown() {
        shutdown = true;
        timer.stop();
        executor.shutdown();
        log.info("TimeWheelRenewalService 已关闭");
    }
}

