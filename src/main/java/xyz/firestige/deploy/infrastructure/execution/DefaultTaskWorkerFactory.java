package xyz.firestige.deploy.infrastructure.execution;

import xyz.firestige.deploy.application.checkpoint.CheckpointService;
import xyz.firestige.deploy.infrastructure.metrics.MetricsRegistry;
import xyz.firestige.deploy.infrastructure.metrics.NoopMetricsRegistry;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;
import xyz.firestige.deploy.infrastructure.state.TaskStateManager;

/**
 * Default implementation of TaskWorkerFactory
 * <p>
 * RF-02: Refactored to use TaskWorkerCreationContext for cleaner parameter passing
 * RF-17: Infrastructure dependencies injected via constructor
 */
public class DefaultTaskWorkerFactory implements TaskWorkerFactory {
    private final MetricsRegistry metrics;
    private final CheckpointService checkpointService;
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
            TaskStateManager stateManager,
            TenantConflictManager conflictManager,
            int progressIntervalSeconds,
            MetricsRegistry metrics) {
        this.checkpointService = checkpointService;
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
            progressIntervalSeconds,
            metrics,
            "heartbeat_lag"
        );

        executor.setHeartbeatScheduler(heartbeat);
        return executor;
    }
}
