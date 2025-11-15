package xyz.firestige.executor.execution;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import xyz.firestige.executor.event.TaskEventSink;

/**
 * 心跳调度器：定期发布任务进度事件，避免长耗时 Stage 阻塞进度。
 */
public class HeartbeatScheduler {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final String planId;
    private final String taskId;
    private final int totalStages;
    private final IntSupplier completedStagesSupplier;
    private final TaskEventSink sink;
    private final int intervalSeconds;
    private volatile boolean stopped;

    public HeartbeatScheduler(String planId, String taskId, int totalStages, IntSupplier completedStagesSupplier, TaskEventSink sink, int intervalSeconds) {
        this.planId = planId;
        this.taskId = taskId;
        this.totalStages = totalStages;
        this.completedStagesSupplier = completedStagesSupplier;
        this.sink = sink;
        this.intervalSeconds = intervalSeconds;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            if (stopped) return;
            int completed = completedStagesSupplier.getAsInt();
            sink.publishTaskProgressDetail(planId, taskId, completed, totalStages, 0);
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        stopped = true;
        scheduler.shutdownNow();
    }
}
