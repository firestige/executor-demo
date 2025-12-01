package xyz.firestige.deploy.infrastructure.execution.strategy;

import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.infrastructure.metrics.MetricsRegistry;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;

/**
 * 执行策略依赖（T-033 简化版 + T-035 无状态执行器）
 * <p>
 * 职责：
 * 将所有依赖封装到一个对象中，避免策略构造函数参数过多
 * <p>
 * T-033: 移除 StateTransitionService，状态转换由聚合根保护
 * T-035: 移除 CheckpointService，无状态执行器不需要内部检查点
 *
 * @since T-032 优化版 - 执行策略模式
 * @since T-035 无状态执行器 - 移除 CheckpointService
 */
public class ExecutionDependencies {

    private final TaskDomainService taskDomainService;
    private final ApplicationEventPublisher technicalEventPublisher;
    private final TenantConflictManager conflictManager;
    private final MetricsRegistry metrics;

    public ExecutionDependencies(
        TaskDomainService taskDomainService,
        ApplicationEventPublisher technicalEventPublisher,
        TenantConflictManager conflictManager,
        MetricsRegistry metrics
    ) {
        this.taskDomainService = taskDomainService;
        this.technicalEventPublisher = technicalEventPublisher;
        this.conflictManager = conflictManager;
        this.metrics = metrics;
    }

    // ========== Getters ==========

    public TaskDomainService getTaskDomainService() {
        return taskDomainService;
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

