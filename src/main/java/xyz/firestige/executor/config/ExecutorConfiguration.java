package xyz.firestige.executor.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.executor.application.DeploymentApplicationService;
import xyz.firestige.executor.application.PlanApplicationService;
import xyz.firestige.executor.application.TaskApplicationService;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.domain.plan.PlanDomainService;
import xyz.firestige.executor.domain.plan.PlanRepository;
import xyz.firestige.executor.domain.task.TaskDomainService;
import xyz.firestige.executor.domain.task.TaskRepository;
import xyz.firestige.executor.domain.stage.DefaultStageFactory;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.execution.DefaultTaskWorkerFactory;
import xyz.firestige.executor.facade.DeploymentTaskFacade;
import xyz.firestige.executor.factory.PlanFactory;
import xyz.firestige.executor.infrastructure.repository.memory.InMemoryPlanRepository;
import xyz.firestige.executor.infrastructure.repository.memory.InMemoryTaskRepository;
import xyz.firestige.executor.orchestration.PlanOrchestrator;
import xyz.firestige.executor.orchestration.TaskScheduler;
import xyz.firestige.executor.service.health.HealthCheckClient;
import xyz.firestige.executor.service.health.MockHealthCheckClient;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.support.conflict.ConflictRegistry;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.validation.validator.BusinessRuleValidator;
import xyz.firestige.executor.validation.validator.ConflictValidator;
import xyz.firestige.executor.validation.validator.NetworkEndpointValidator;
import xyz.firestige.executor.validation.validator.TenantIdValidator;

/**
 * 执行器配置类（DDD 重构版）
 *
 * 重构说明：
 * - 新增 Repository Bean (InMemoryPlanRepository, InMemoryTaskRepository)
 * - 新增 DomainService Bean (PlanDomainService, TaskDomainService)
 * - 新增 ApplicationService Bean (DeploymentApplicationService)
 * - 保留旧 Bean 以保持向后兼容（标记为 @Deprecated）
 *
 * @since DDD 重构 Phase 2.2.5
 */
@Configuration
public class ExecutorConfiguration {

    // ========== 基础设施 Bean ==========

    @Bean
    public TaskStateManager taskStateManager(ApplicationEventPublisher eventPublisher) {
        return new TaskStateManager(eventPublisher);
    }

    @Bean
    public ValidationChain validationChain() {
        ValidationChain chain = new ValidationChain(false);
        chain.addValidator(new TenantIdValidator());
        chain.addValidator(new NetworkEndpointValidator());
        chain.addValidator(new ConflictValidator());
        chain.addValidator(new BusinessRuleValidator());
        return chain;
    }

    @Bean
    public ExecutorProperties executorProperties() {
        return new ExecutorProperties();
    }

    @Bean
    public HealthCheckClient healthCheckClient() {
        return new MockHealthCheckClient();
    }

    @Bean
    public ConflictRegistry conflictRegistry() {
        return new ConflictRegistry();
    }

    @Bean
    public CheckpointService checkpointService() {
        return new CheckpointService(new InMemoryCheckpointStore());
    }

    @Bean
    public SpringTaskEventSink springTaskEventSink(
            TaskStateManager stateManager,
            ConflictRegistry conflictRegistry) {
        return new SpringTaskEventSink(stateManager, conflictRegistry);
    }

    // ========== Repository Bean (DDD 重构新增) ==========

    @Bean
    public PlanRepository planRepository() {
        return new InMemoryPlanRepository();
    }

    @Bean
    public TaskRepository taskRepository() {
        return new InMemoryTaskRepository();
    }

    // ========== Domain Service Bean (DDD 重构新增) ==========

    @Bean
    public PlanDomainService planDomainService(
            PlanRepository planRepository,
            TaskStateManager stateManager,
            ExecutorProperties executorProperties,
            ConflictRegistry conflictRegistry,
            SpringTaskEventSink eventSink) {
        return new PlanDomainService(
                planRepository,
                stateManager,
                new PlanFactory(),
                new PlanOrchestrator(
                    new TaskScheduler(Runtime.getRuntime().availableProcessors()),
                    conflictRegistry,
                    executorProperties
                ),
                eventSink,
                executorProperties
        );
    }

    @Bean
    public TaskDomainService taskDomainService(
            TaskRepository taskRepository,
            TaskStateManager stateManager,
            ExecutorProperties executorProperties,
            CheckpointService checkpointService,
            SpringTaskEventSink eventSink,
            ConflictRegistry conflictRegistry) {
        return new TaskDomainService(
                taskRepository,
                stateManager,
                new DefaultTaskWorkerFactory(),
                executorProperties,
                checkpointService,
                eventSink,
                conflictRegistry
        );
    }

    // ========== Application Service Bean (DDD 重构新增) ==========

    @Bean
    public DeploymentApplicationService deploymentApplicationService(
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            ValidationChain validationChain) {
        return new DeploymentApplicationService(
                planDomainService,
                taskDomainService,
                validationChain
        );
    }

    // ========== Facade Bean ==========

    @Bean
    public DeploymentTaskFacade deploymentTaskFacade(
            DeploymentApplicationService deploymentApplicationService) {
        return new DeploymentTaskFacade(deploymentApplicationService);
    }
}

