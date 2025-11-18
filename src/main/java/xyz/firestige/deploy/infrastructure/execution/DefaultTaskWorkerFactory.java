package xyz.firestige.deploy.infrastructure.execution;

import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.deploy.application.checkpoint.CheckpointService;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.StateTransitionService;
import xyz.firestige.deploy.infrastructure.metrics.MetricsRegistry;
import xyz.firestige.deploy.infrastructure.metrics.NoopMetricsRegistry;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;

/**
 * Default implementation of TaskWorkerFactory
 * <p>
 * RF-02: Refactored to use TaskWorkerCreationContext for cleaner parameter passing
 * RF-17: Infrastructure dependencies injected via constructor
 * RF-18: 方案C架构 - 注入 TaskDomainService + StateTransitionService
 */
public class DefaultTaskWorkerFactory implements TaskWorkerFactory {
    private final TaskDomainService taskDomainService;
    private final StateTransitionService stateTransitionService;
    private final ApplicationEventPublisher technicalEventPublisher;
    private final CheckpointService checkpointService;
    private final TenantConflictManager conflictManager;
    private final int progressIntervalSeconds;
    private final MetricsRegistry metrics;

    /**
     * RF-18: 构造函数（方案C架构）
     *
     * @param taskDomainService Domain service for task operations
     * @param stateTransitionService State transition validation service
     * @param technicalEventPublisher Spring event publisher for monitoring events
     * @param checkpointService Checkpoint service
     * @param conflictManager Tenant conflict manager
     * @param progressIntervalSeconds Progress interval in seconds
     * @param metrics Metrics registry
     */
    public DefaultTaskWorkerFactory(
            TaskDomainService taskDomainService,
            StateTransitionService stateTransitionService,
            ApplicationEventPublisher technicalEventPublisher,
            CheckpointService checkpointService,
            TenantConflictManager conflictManager,
            int progressIntervalSeconds,
            MetricsRegistry metrics) {
        this.taskDomainService = taskDomainService;
        this.stateTransitionService = stateTransitionService;
        this.technicalEventPublisher = technicalEventPublisher;
        this.checkpointService = checkpointService;
        this.conflictManager = conflictManager;
        this.progressIntervalSeconds = progressIntervalSeconds;
        this.metrics = metrics != null ? metrics : new NoopMetricsRegistry();
    }

    /**
     * RF-18: 创建 TaskExecutor（基于方案C架构）
     * 
     * <p>注入依赖：
     * <ul>
     *   <li>TaskDomainService - 封装 save + publish 逻辑</li>
     *   <li>StateTransitionService - 前置检查</li>
     *   <li>ApplicationEventPublisher - 监控事件</li>
     * </ul>
     */
    @Override
    public TaskExecutor create(TaskWorkerCreationContext context) {
        TaskExecutor executor = new TaskExecutor(
            context.getPlanId(),
            context.getTask(),
            context.getStages(),
            context.getRuntimeContext(),
            taskDomainService,
            stateTransitionService,
            technicalEventPublisher,
            checkpointService,
            conflictManager,
            progressIntervalSeconds,
            metrics
        );

        // RF-18: 创建心跳调度器（新架构）
        HeartbeatScheduler heartbeat = new HeartbeatScheduler(
            context.getTask(),
            technicalEventPublisher,
            progressIntervalSeconds,
            metrics
        );

        executor.setHeartbeatScheduler(heartbeat);
        return executor;
    }
}
