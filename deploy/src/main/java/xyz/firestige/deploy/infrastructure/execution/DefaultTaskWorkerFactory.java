package xyz.firestige.deploy.infrastructure.execution;

import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.deploy.application.checkpoint.CheckpointService;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.infrastructure.execution.strategy.ExecutionDependencies;
import xyz.firestige.deploy.infrastructure.execution.strategy.ExecutionPreparer;
import xyz.firestige.deploy.infrastructure.metrics.MetricsRegistry;
import xyz.firestige.deploy.infrastructure.metrics.NoopMetricsRegistry;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;

/**
 * Default implementation of TaskWorkerFactory
 * <p>
 * RF-02: Refactored to use TaskWorkerCreationContext for cleaner parameter passing
 * RF-17: Infrastructure dependencies injected via constructor
 * RF-18: 方案C架构 - 注入 TaskDomainService
 * T-032: 使用 ExecutionPreparer + ExecutionDependencies 准备器模式
 * T-033: 移除 StateTransitionService，状态转换由聚合根保护
 */
public class DefaultTaskWorkerFactory implements TaskWorkerFactory {
    private final TaskDomainService taskDomainService;
    private final ApplicationEventPublisher technicalEventPublisher;
    private final CheckpointService checkpointService;
    private final TenantConflictManager conflictManager;
    private final int progressIntervalSeconds;
    private final MetricsRegistry metrics;

    /**
     * T-033: 构造函数（移除 StateTransitionService）
     *
     * @param taskDomainService Domain service for task operations
     * @param technicalEventPublisher Spring event publisher for monitoring events
     * @param checkpointService Checkpoint service
     * @param conflictManager Tenant conflict manager
     * @param progressIntervalSeconds Progress interval in seconds
     * @param metrics Metrics registry
     */
    public DefaultTaskWorkerFactory(
            TaskDomainService taskDomainService,
            ApplicationEventPublisher technicalEventPublisher,
            CheckpointService checkpointService,
            TenantConflictManager conflictManager,
            int progressIntervalSeconds,
            MetricsRegistry metrics) {
        this.taskDomainService = taskDomainService;
        this.technicalEventPublisher = technicalEventPublisher;
        this.checkpointService = checkpointService;
        this.conflictManager = conflictManager;
        this.progressIntervalSeconds = progressIntervalSeconds;
        this.metrics = metrics != null ? metrics : new NoopMetricsRegistry();
    }

    /**
     * T-033: 创建 TaskExecutor（移除 StateTransitionService）
     *
     * <p>核心变化：
     * <ul>
     *   <li>创建 ExecutionPreparer - 统一的准备逻辑</li>
     *   <li>创建 ExecutionDependencies - 封装所有依赖</li>
     *   <li>简化 TaskExecutor 构造函数</li>
     * </ul>
     */
    @Override
    public TaskExecutor create(TaskWorkerCreationContext context) {
        // ✅ T-032: 创建准备器
        ExecutionPreparer preparer = new ExecutionPreparer();

        // ✅ T-033: 封装依赖（移除 stateTransitionService）
        ExecutionDependencies dependencies = new ExecutionDependencies(
            taskDomainService,
            checkpointService,
            technicalEventPublisher,
            conflictManager,
            metrics
        );

        // ✅ T-032: 创建 TaskExecutor（简化版构造函数）
        TaskExecutor executor = getTaskExecutor(context, preparer, dependencies);
        return executor;
    }

    private TaskExecutor getTaskExecutor(TaskWorkerCreationContext context, ExecutionPreparer preparer, ExecutionDependencies dependencies) {
        TaskExecutor executor = new TaskExecutor(
            context.getPlanId(),
            context.getTask(),
            context.getStages(),
            context.getRuntimeContext(),
                preparer,
                dependencies,
            progressIntervalSeconds
        );

        // 创建心跳调度器
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
