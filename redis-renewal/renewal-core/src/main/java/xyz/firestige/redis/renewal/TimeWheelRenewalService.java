package xyz.firestige.redis.renewal;

import io.netty.util.HashedWheelTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.redis.renewal.listener.LifecycleListener;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
        if (task.getListener() != null) {
            task.getListener().onTaskRegistered(id, task);
        }
        log.info("注册续期任务: id={}, initialDelay={}ms", id, initialDelay(task));
        return id;
    }

    private long initialDelay(RenewalTask task) {
        // 使用临时上下文计算初始延迟
        RenewalContext tempCtx = new RenewalContext(task.getTaskId() != null ? task.getTaskId() : "temp");
        Duration interval = task.getIntervalStrategy().nextInterval(tempCtx);
        return Math.max(interval.toMillis(), 10); // 最小 10ms 保护
    }

    private void schedule(RenewalTaskWrapper wrapper, long delayMs) {
        timer.newTimeout(timeout -> onTick(wrapper), delayMs, TimeUnit.MILLISECONDS);
    }

    private void onTick(RenewalTaskWrapper wrapper) {
        if (shutdown) return;
        RenewalTask task = wrapper.task();
        RenewalContext ctx = wrapper.context();
        // 如果暂停：仍然重新调度（保持任务活跃可恢复）
        if (wrapper.paused()) {
            Duration pauseInterval = task.getIntervalStrategy().nextInterval(ctx);
            schedule(wrapper, Math.max(pauseInterval.toMillis(), 10));
            return;
        }
        // 获取 Key 集合并计算 TTL
        Collection<String> keys = task.getKeySelector().selectKeys(ctx);
        Duration ttl = task.getTtlStrategy().nextTtl(ctx);
        long ttlSeconds = Math.max(ttl.getSeconds(), 1);

        // 记录 TTL 供自适应策略使用
        ctx.setLastTtl(ttl);

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

                    boolean stopByCondition = task.getStopStrategy() != null && task.getStopStrategy().shouldStop(ctx);
                    if (stopByCondition) {
                        complete(wrapper, LifecycleListener.CompletionReason.STOP_CONDITION_MET);
                        return;
                    }
                    // 安排下次调度
                    Duration nextInterval = task.getIntervalStrategy().nextInterval(ctx);
                    schedule(wrapper, Math.max(nextInterval.toMillis(), 10));
                });
    }

    private void complete(RenewalTaskWrapper wrapper, LifecycleListener.CompletionReason reason) {
        tasks.remove(wrapper.id());
        if (wrapper.task().getListener() != null) wrapper.task().getListener().onTaskCompleted(wrapper.id(), reason);
        log.info("续期任务完成: id={}, reason={}", wrapper.id(), reason);
    }

    @Override
    public void cancel(String taskId) {
        RenewalTaskWrapper w = tasks.remove(taskId);
        if (w != null) {
            complete(w, LifecycleListener.CompletionReason.CANCELLED);
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
        return w.paused() ? RenewalTaskStatus.PAUSED : RenewalTaskStatus.RUNNING;
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

