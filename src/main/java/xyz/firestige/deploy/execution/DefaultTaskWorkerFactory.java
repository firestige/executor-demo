package xyz.firestige.deploy.execution;

import xyz.firestige.deploy.checkpoint.CheckpointService;
import xyz.firestige.deploy.domain.stage.TaskStage;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.event.TaskEventSink;
import xyz.firestige.deploy.metrics.MetricsRegistry;
import xyz.firestige.deploy.metrics.NoopMetricsRegistry;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.support.conflict.TenantConflictManager;

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
            context.getConflictManager(),
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
                               TenantConflictManager conflictManager) {
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
            .conflictManager(conflictManager)
            .build();

        return create(context);
    }
}
