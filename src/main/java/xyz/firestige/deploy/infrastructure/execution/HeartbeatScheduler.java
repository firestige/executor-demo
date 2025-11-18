package xyz.firestige.deploy.infrastructure.execution;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.StageProgress;
import xyz.firestige.deploy.infrastructure.event.monitoring.TaskProgressMonitoringEvent;
import xyz.firestige.deploy.infrastructure.metrics.MetricsRegistry;
import xyz.firestige.deploy.infrastructure.metrics.NoopMetricsRegistry;

/**
 * 心跳调度器：定期发布任务进度监控事件
 * 
 * <p>RF-18 重构特点：
 * <ul>
 *   <li>只读取 TaskAggregate 状态（不修改）</li>
 *   <li>发布技术监控事件（TaskProgressMonitoringEvent）</li>
 *   <li>高频发布（每 10 秒），不影响领域事件</li>
 * </ul>
 * 
 * @since RF-18: 事件驱动架构重构
 */
public class HeartbeatScheduler {

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final TaskAggregate task;
    private final ApplicationEventPublisher eventPublisher;
    private final int intervalSeconds;
    private volatile boolean stopped;
    private ScheduledFuture<?> future;
    private volatile boolean started;
    private final MetricsRegistry metrics;
    private final String gaugeName;

    /**
     * RF-18: 新构造函数（基于 TaskAggregate）
     */
    public HeartbeatScheduler(
            TaskAggregate task,
            ApplicationEventPublisher eventPublisher,
            int intervalSeconds,
            MetricsRegistry metrics) {
        this.task = task;
        this.eventPublisher = eventPublisher;
        this.intervalSeconds = intervalSeconds;
        this.metrics = metrics != null ? metrics : new NoopMetricsRegistry();
        this.gaugeName = "task_heartbeat_lag";
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
            try {
                // ✅ 只读取聚合状态
                StageProgress progress = task.getStageProgress();
                if (progress == null) {
                    return;  // 还未初始化
                }
                
                // ✅ 发布监控事件
                TaskProgressMonitoringEvent event = new TaskProgressMonitoringEvent(
                    task.getTaskId(),
                    progress.getCurrentStageIndex(),
                    progress.getTotalStages(),
                    progress.getProgressPercentage(),
                    task.getStatus(),
                    LocalDateTime.now()
                );
                
                eventPublisher.publishEvent(event);
                
                // 更新 metrics
                int lag = Math.max(0, progress.getTotalStages() - progress.getCurrentStageIndex());
                metrics.setGauge(gaugeName, lag);
                
            } catch (Exception e) {
                // 心跳失败不影响主流程
            }
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
