package xyz.firestige.deploy.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.deploy.application.DeploymentApplicationService;
import xyz.firestige.deploy.application.validation.BusinessValidator;
import xyz.firestige.deploy.checkpoint.CheckpointService;
import xyz.firestige.deploy.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.deploy.domain.plan.PlanDomainService;
import xyz.firestige.deploy.domain.plan.PlanRepository;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.TaskRepository;
import xyz.firestige.deploy.domain.stage.DefaultStageFactory;
import xyz.firestige.deploy.event.SpringTaskEventSink;
import xyz.firestige.deploy.execution.DefaultTaskWorkerFactory;
import xyz.firestige.deploy.facade.DeploymentTaskFacade;
import xyz.firestige.deploy.factory.PlanFactory;
import xyz.firestige.deploy.infrastructure.repository.memory.InMemoryPlanRepository;
import xyz.firestige.deploy.infrastructure.repository.memory.InMemoryTaskRepository;
import xyz.firestige.deploy.orchestration.PlanOrchestrator;
import xyz.firestige.deploy.orchestration.TaskScheduler;
import xyz.firestige.deploy.service.health.HealthCheckClient;
import xyz.firestige.deploy.service.health.MockHealthCheckClient;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.support.conflict.ConflictRegistry;
import xyz.firestige.deploy.validation.ValidationChain;
import xyz.firestige.deploy.validation.validator.BusinessRuleValidator;
import xyz.firestige.deploy.validation.validator.ConflictValidator;
import xyz.firestige.deploy.validation.validator.NetworkEndpointValidator;
import xyz.firestige.deploy.validation.validator.TenantIdValidator;

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
    public Validator validator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        return factory.getValidator();
    }

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

    @Bean
    public xyz.firestige.deploy.domain.task.TaskRuntimeRepository taskRuntimeRepository() {
        return new xyz.firestige.deploy.infrastructure.repository.memory.InMemoryTaskRuntimeRepository();
    }

    // ========== Domain Service Bean (DDD 重构新增) ==========

    @Bean
    public PlanDomainService planDomainService(
            PlanRepository planRepository,
            TaskStateManager stateManager,
            ExecutorProperties executorProperties,
            ConflictRegistry conflictRegistry,
            SpringTaskEventSink eventSink,
            xyz.firestige.deploy.event.DomainEventPublisher domainEventPublisher) {
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
                executorProperties,
                domainEventPublisher
        );
    }

    @Bean
    public TaskDomainService taskDomainService(
            TaskRepository taskRepository,
            xyz.firestige.deploy.domain.task.TaskRuntimeRepository taskRuntimeRepository,
            TaskStateManager stateManager,
            ExecutorProperties executorProperties,
            CheckpointService checkpointService,
            SpringTaskEventSink eventSink,
            ConflictRegistry conflictRegistry,
            xyz.firestige.deploy.event.DomainEventPublisher domainEventPublisher) {
        return new TaskDomainService(
                taskRepository,
                taskRuntimeRepository,
                stateManager,
                new DefaultTaskWorkerFactory(),
                executorProperties,
                checkpointService,
                eventSink,
                conflictRegistry,
                domainEventPublisher
        );
    }

    // ========== Application Service Bean (DDD 重构新增) ==========

    @Bean
    public xyz.firestige.deploy.application.plan.DeploymentPlanCreator deploymentPlanCreator(
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            HealthCheckClient healthCheckClient,
            BusinessValidator businessValidator) {
        return new xyz.firestige.deploy.application.plan.DeploymentPlanCreator(
                planDomainService,
                taskDomainService,
                new DefaultStageFactory(),
                healthCheckClient,
                businessValidator
        );
    }

    @Bean
    public DeploymentApplicationService deploymentApplicationService(
            xyz.firestige.deploy.application.plan.DeploymentPlanCreator deploymentPlanCreator,
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            xyz.firestige.deploy.orchestration.strategy.PlanSchedulingStrategy planSchedulingStrategy,
            xyz.firestige.deploy.support.conflict.ConflictRegistry conflictRegistry) {
        return new DeploymentApplicationService(
                deploymentPlanCreator,
                planDomainService,
                taskDomainService,
                planSchedulingStrategy,
                conflictRegistry
        );
    }

    // ========== Facade Bean ==========

    @Bean
    public DeploymentTaskFacade deploymentTaskFacade(
            DeploymentApplicationService deploymentApplicationService,
            Validator validator) {  // Jakarta Validator
        return new DeploymentTaskFacade(deploymentApplicationService, validator);
    }
}

