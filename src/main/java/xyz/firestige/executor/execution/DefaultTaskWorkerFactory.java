package xyz.firestige.executor.execution;

import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.event.TaskEventSink;
import xyz.firestige.executor.metrics.MetricsRegistry;
import xyz.firestige.executor.metrics.NoopMetricsRegistry;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.support.conflict.ConflictRegistry;

import java.util.List;

public class DefaultTaskWorkerFactory implements TaskWorkerFactory {
    private final MetricsRegistry metrics;
    public DefaultTaskWorkerFactory() { this.metrics = new NoopMetricsRegistry(); }
    public DefaultTaskWorkerFactory(MetricsRegistry metrics) { this.metrics = metrics != null ? metrics : new NoopMetricsRegistry(); }
    @Override
    public TaskExecutor create(String planId,
                               TaskAggregate task,
                               List<TaskStage> stages,
                               TaskRuntimeContext ctx,
                               CheckpointService checkpointService,
                               TaskEventSink eventSink,
                               int progressIntervalSeconds,
                               TaskStateManager stateManager,
                               ConflictRegistry conflicts) {
        TaskExecutor executor = new TaskExecutor(planId, task, stages, ctx, checkpointService, eventSink, progressIntervalSeconds, stateManager, conflicts, metrics);
        HeartbeatScheduler heartbeat = new HeartbeatScheduler(planId, task.getTaskId(), stages.size(), executor::getCompletedStageCount, eventSink, progressIntervalSeconds, metrics, "heartbeat_lag");
        executor.setHeartbeatScheduler(heartbeat);
        return executor;
    }
}
