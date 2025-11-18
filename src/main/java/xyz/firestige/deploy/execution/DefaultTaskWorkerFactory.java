package xyz.firestige.deploy.execution;

import java.util.List;

import xyz.firestige.deploy.checkpoint.CheckpointService;
import xyz.firestige.deploy.domain.stage.TaskStage;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.event.TaskEventSink;
import xyz.firestige.deploy.metrics.MetricsRegistry;
import xyz.firestige.deploy.metrics.NoopMetricsRegistry;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.support.conflict.TenantConflictManager;

/**
 * Default implementation of TaskWorkerFactory
 *
 * RF-02: Refactored to use TaskWorkerCreationContext for cleaner parameter passing
 * RF-17: Infrastructure dependencies injected via constructor
 */
public class DefaultTaskWorkerFactory implements TaskWorkerFactory {
    private final MetricsRegistry metrics;
    private final CheckpointService checkpointService;
    private final TaskEventSink eventSink;
    private final TaskStateManager stateManager;
    private final TenantConflictManager conflictManager;
    private final int progressIntervalSeconds;

    /**
     * Constructor with all infrastructure dependencies
     *
     * @param checkpointService Checkpoint service
     * @param eventSink Task event sink
     * @param stateManager Task state manager
     * @param conflictManager Tenant conflict manager
     * @param progressIntervalSeconds Progress interval in seconds
     * @param metrics Metrics registry
     */
    public DefaultTaskWorkerFactory(
            CheckpointService checkpointService,
            TaskEventSink eventSink,
            TaskStateManager stateManager,
            TenantConflictManager conflictManager,
            int progressIntervalSeconds,
            MetricsRegistry metrics) {
        this.checkpointService = checkpointService;
        this.eventSink = eventSink;
        this.stateManager = stateManager;
        this.conflictManager = conflictManager;
        this.progressIntervalSeconds = progressIntervalSeconds;
        this.metrics = metrics != null ? metrics : new NoopMetricsRegistry();
    }

    /**
     * Create TaskExecutor with creation context (RF-02 recommended approach)
     * RF-17: Infrastructure dependencies from constructor, context only provides domain data
     */
    @Override
    public TaskExecutor create(TaskWorkerCreationContext context) {
        TaskExecutor executor = new TaskExecutor(
            context.getPlanId(),
            context.getTask(),
            context.getStages(),
            context.getRuntimeContext(),
            checkpointService,
            eventSink,
            progressIntervalSeconds,
            stateManager,
            conflictManager,
            metrics
        );

        HeartbeatScheduler heartbeat = new HeartbeatScheduler(
            context.getPlanId(),
            context.getTask().getTaskId(),
            context.getStages().size(),
            executor::getCompletedStageCount,
            eventSink,
            progressIntervalSeconds,
            metrics,
            "heartbeat_lag"
        );

        executor.setHeartbeatScheduler(heartbeat);
        return executor;
    }

    /**
     * Create TaskExecutor with individual parameters (legacy method for backward compatibility)
     * RF-17: Infrastructure dependencies ignored (use constructor-injected ones instead)
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
        // Delegate to context-based method (ignore passed infrastructure dependencies)
        TaskWorkerCreationContext context = TaskWorkerCreationContext.builder()
            .planId(planId)
            .task(task)
            .stages(stages)
            .runtimeContext(ctx)
            .build();

        return create(context);
    }
}
