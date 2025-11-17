package xyz.firestige.executor.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.executor.application.PlanApplicationService;
import xyz.firestige.executor.application.TaskApplicationService;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.domain.stage.DefaultStageFactory;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.execution.DefaultTaskWorkerFactory;
import xyz.firestige.executor.facade.DeploymentTaskFacade;
import xyz.firestige.executor.factory.PlanFactory;
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
 * 执行器配置类（RF01 重构版）
 */
@Configuration
public class ExecutorConfiguration {

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
    public PlanApplicationService planApplicationService(
            ValidationChain validationChain,
            TaskStateManager stateManager,
            ExecutorProperties executorProperties,
            HealthCheckClient healthCheckClient,
            CheckpointService checkpointService,
            ConflictRegistry conflictRegistry) {
        return new PlanApplicationService(
                validationChain,
                stateManager,
                new PlanFactory(),
                new PlanOrchestrator(new TaskScheduler(Runtime.getRuntime().availableProcessors()), conflictRegistry, executorProperties),
                new DefaultStageFactory(),
                new DefaultTaskWorkerFactory(),
                executorProperties,
                healthCheckClient,
                checkpointService,
                new SpringTaskEventSink(stateManager, conflictRegistry),
                conflictRegistry
        );
    }

    @Bean
    public TaskApplicationService taskApplicationService(
            PlanApplicationService planApplicationService,
            TaskStateManager stateManager,
            ExecutorProperties executorProperties,
            CheckpointService checkpointService,
            ConflictRegistry conflictRegistry) {
        SpringTaskEventSink eventSink = new SpringTaskEventSink(stateManager, conflictRegistry);
        return new TaskApplicationService(
                stateManager,
                new DefaultTaskWorkerFactory(),
                executorProperties,
                checkpointService,
                eventSink,
                conflictRegistry,
                planApplicationService.getTaskRegistry(),
                planApplicationService.getContextRegistry(),
                planApplicationService.getStageRegistry(),
                planApplicationService.getExecutorRegistry()
        );
    }

    @Bean
    public DeploymentTaskFacade deploymentTaskFacade(
            PlanApplicationService planApplicationService,
            TaskApplicationService taskApplicationService) {
        return new DeploymentTaskFacade(planApplicationService, taskApplicationService);
    }
}

