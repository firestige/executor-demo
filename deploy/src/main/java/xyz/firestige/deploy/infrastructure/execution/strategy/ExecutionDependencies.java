package xyz.firestige.deploy.infrastructure.execution.strategy;

import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.deploy.application.checkpoint.CheckpointService;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.infrastructure.metrics.MetricsRegistry;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;

/**
 * 执行策略依赖（T-033 简化版）
 * <p>
 * 职责：
 * 将所有依赖封装到一个对象中，避免策略构造函数参数过多
 * <p>
 * T-033: 移除 StateTransitionService，状态转换由聚合根保护
 *
 * @since T-032 优化版 - 执行策略模式
 */
public class ExecutionDependencies {

    private final TaskDomainService taskDomainService;
    private final CheckpointService checkpointService;
    private final ApplicationEventPublisher technicalEventPublisher;
    private final TenantConflictManager conflictManager;
    private final MetricsRegistry metrics;

    public ExecutionDependencies(
        TaskDomainService taskDomainService,
        CheckpointService checkpointService,
        ApplicationEventPublisher technicalEventPublisher,
        TenantConflictManager conflictManager,
        MetricsRegistry metrics
    ) {
        this.taskDomainService = taskDomainService;
        this.checkpointService = checkpointService;
        this.technicalEventPublisher = technicalEventPublisher;
        this.conflictManager = conflictManager;
        this.metrics = metrics;
    }

    // ========== Getters ==========

    public TaskDomainService getTaskDomainService() {
        return taskDomainService;
    }

    public CheckpointService getCheckpointService() {
        return checkpointService;
    }

    public ApplicationEventPublisher getTechnicalEventPublisher() {
        return technicalEventPublisher;
    }

    public TenantConflictManager getConflictManager() {
        return conflictManager;
    }

    public MetricsRegistry getMetrics() {
        return metrics;
    }
}

