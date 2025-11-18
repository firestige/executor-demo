package xyz.firestige.deploy.infrastructure.execution;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import xyz.firestige.deploy.infrastructure.metrics.MetricsRegistry;
import xyz.firestige.deploy.infrastructure.metrics.NoopMetricsRegistry;

/**
 * 心跳调度器：定期发布任务进度事件，避免长耗时 Stage 阻塞进度。
 */
public class HeartbeatScheduler {

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final String planId;
    private final String taskId;
    private final int totalStages;
    private final IntSupplier completedStagesSupplier;
    private final int intervalSeconds;
    private volatile boolean stopped;
    private ScheduledFuture<?> future;
    private volatile boolean started;
    private final MetricsRegistry metrics;
    private final String gaugeName;

    public HeartbeatScheduler(String planId, String taskId, int totalStages, IntSupplier completedStagesSupplier, int intervalSeconds) {
        this(planId, taskId, totalStages, completedStagesSupplier, intervalSeconds, new NoopMetricsRegistry(), "heartbeat_lag");
    }

    public HeartbeatScheduler(String planId,
                              String taskId,
                              int totalStages,
                              IntSupplier completedStagesSupplier,
                              int intervalSeconds,
                              MetricsRegistry metrics,
                              String gaugeName) {
        this.planId = planId;
        this.taskId = taskId;
        this.totalStages = totalStages;
        this.completedStagesSupplier = completedStagesSupplier;
        this.intervalSeconds = intervalSeconds;
        this.metrics = metrics != null ? metrics : new NoopMetricsRegistry();
        this.gaugeName = (gaugeName == null || gaugeName.isEmpty()) ? "heartbeat_lag" : gaugeName;
    }

    public synchronized void start() {
        if (started && !isExecutorTerminated()) return;
        if (isExecutorTerminated()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        stopped = false;
        started = true;
        future = scheduler.scheduleAtFixedRate(() -> {
            if (stopped) return;
            int completed = completedStagesSupplier.getAsInt();
            int lag = Math.max(0, totalStages - completed);
            metrics.setGauge(gaugeName, lag);
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        stopped = true;
        started = false;
        if (future != null) future.cancel(true);
        // 不关闭 scheduler，允许后续重试再次 start；若外部已经关闭，start 会重建
    }

    public boolean isRunning() { return started && !stopped; }

    private boolean isExecutorTerminated() { return scheduler.isShutdown() || scheduler.isTerminated(); }
}
