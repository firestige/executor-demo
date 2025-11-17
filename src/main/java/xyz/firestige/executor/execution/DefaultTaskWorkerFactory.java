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

/**
 * Default implementation of TaskWorkerFactory
 *
 * RF-02: Refactored to use TaskWorkerCreationContext for cleaner parameter passing
 */
public class DefaultTaskWorkerFactory implements TaskWorkerFactory {
    private final MetricsRegistry metrics;

    public DefaultTaskWorkerFactory() {
        this.metrics = new NoopMetricsRegistry();
    }

    public DefaultTaskWorkerFactory(MetricsRegistry metrics) {
        this.metrics = metrics != null ? metrics : new NoopMetricsRegistry();
    }

    /**
     * Create TaskExecutor with creation context (RF-02 recommended approach)
     */
    @Override
    public TaskExecutor create(TaskWorkerCreationContext context) {
        TaskExecutor executor = new TaskExecutor(
            context.getPlanId(),
            context.getTask(),
            context.getStages(),
            context.getRuntimeContext(),
            context.getCheckpointService(),
            context.getEventSink(),
            context.getProgressIntervalSeconds(),
            context.getStateManager(),
            context.getConflictRegistry(),
            metrics
        );

        HeartbeatScheduler heartbeat = new HeartbeatScheduler(
            context.getPlanId(),
            context.getTask().getTaskId(),
            context.getStages().size(),
            executor::getCompletedStageCount,
            context.getEventSink(),
            context.getProgressIntervalSeconds(),
            metrics,
            "heartbeat_lag"
        );

        executor.setHeartbeatScheduler(heartbeat);
        return executor;
    }

    /**
     * Create TaskExecutor with individual parameters (legacy method for backward compatibility)
     *
     * @deprecated Use {@link #create(TaskWorkerCreationContext)} instead
     */
    @Deprecated
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
        // Delegate to context-based method
        TaskWorkerCreationContext context = TaskWorkerCreationContext.builder()
            .planId(planId)
            .task(task)
            .stages(stages)
            .runtimeContext(ctx)
            .checkpointService(checkpointService)
            .eventSink(eventSink)
            .progressIntervalSeconds(progressIntervalSeconds)
            .stateManager(stateManager)
            .conflictRegistry(conflicts)
            .build();

        return create(context);
    }
}
