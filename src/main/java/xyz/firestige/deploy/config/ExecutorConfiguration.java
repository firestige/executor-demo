package xyz.firestige.deploy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import xyz.firestige.deploy.application.DeploymentApplicationService;
import xyz.firestige.deploy.application.plan.DeploymentPlanCreator;
import xyz.firestige.deploy.application.validation.BusinessValidator;
import xyz.firestige.deploy.application.checkpoint.CheckpointService;
import xyz.firestige.deploy.infrastructure.execution.DefaultTaskWorkerFactory;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerFactory;
import xyz.firestige.deploy.infrastructure.external.health.HealthCheckClient;
import xyz.firestige.deploy.infrastructure.external.health.MockHealthCheckClient;
import xyz.firestige.deploy.infrastructure.persistence.checkpoint.InMemoryCheckpointRepository;
import xyz.firestige.deploy.domain.plan.PlanDomainService;
import xyz.firestige.deploy.domain.plan.PlanRepository;
import xyz.firestige.deploy.infrastructure.execution.stage.DefaultStageFactory;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.TaskRepository;
import xyz.firestige.deploy.domain.task.TaskRuntimeRepository;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;
import xyz.firestige.deploy.facade.DeploymentTaskFacade;
import xyz.firestige.deploy.infrastructure.persistence.plan.InMemoryPlanRepository;
import xyz.firestige.deploy.infrastructure.persistence.task.InMemoryTaskRepository;
import xyz.firestige.deploy.infrastructure.persistence.task.InMemoryTaskRuntimeRepository;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;
import xyz.firestige.deploy.application.validation.ConflictValidator;
import xyz.firestige.deploy.infrastructure.state.TaskStateManager;
import xyz.firestige.deploy.infrastructure.validation.ValidationChain;
import xyz.firestige.deploy.infrastructure.validation.validator.BusinessRuleValidator;
import xyz.firestige.deploy.infrastructure.validation.validator.NetworkEndpointValidator;
import xyz.firestige.deploy.infrastructure.validation.validator.TenantIdValidator;

/**
 * 执行器配置类（DDD 重构版）
 * <p>
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
    public TaskStateManager taskStateManager() {
        return new TaskStateManager();
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
    public TenantConflictManager conflictManager() {
        // TODO 按配置来，不要写死
        return new TenantConflictManager(TenantConflictManager.ConflictPolicy.FINE_GRAINED);
    }

    @Bean
    public CheckpointService checkpointService() {
        return new CheckpointService(new InMemoryCheckpointRepository());
    }

    @Bean
    public TaskWorkerFactory taskWorkerFactory(
            CheckpointService checkpointService,
            TaskStateManager stateManager,
            TenantConflictManager conflictManager,
            ExecutorProperties executorProperties) {
        return new DefaultTaskWorkerFactory(
                checkpointService,
                stateManager,
                conflictManager,
                executorProperties.getTaskProgressIntervalSeconds(),
                null  // metrics: 使用默认的 NoopMetricsRegistry
        );
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
    public TaskRuntimeRepository taskRuntimeRepository() {
        return new InMemoryTaskRuntimeRepository();
    }

    // ========== Domain Service Bean (DDD 重构新增) ==========

    @Bean
    public PlanDomainService planDomainService(
            PlanRepository planRepository,
            DomainEventPublisher domainEventPublisher) {
        return new PlanDomainService(
                planRepository,
                domainEventPublisher
        );
    }

    @Bean
    public TaskDomainService taskDomainService(
            TaskRepository taskRepository,
            xyz.firestige.deploy.domain.task.TaskRuntimeRepository taskRuntimeRepository,
            TaskStateManager stateManager,
            DomainEventPublisher domainEventPublisher) {
        return new TaskDomainService(
                taskRepository,
                taskRuntimeRepository,
                stateManager,
                domainEventPublisher
        );
    }

    // ========== Application Service Bean (DDD 重构新增) ==========

    @Bean
    public DeploymentPlanCreator deploymentPlanCreator(
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            BusinessValidator businessValidator,
            ExecutorProperties executorProperties) {
        return new DeploymentPlanCreator(
                planDomainService,
                taskDomainService,
                new DefaultStageFactory(),
                businessValidator,
                executorProperties
        );
    }

    @Bean
    public DeploymentApplicationService deploymentApplicationService(
            DeploymentPlanCreator deploymentPlanCreator,
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            TenantConflictManager conflictManager,
            TaskWorkerFactory taskWorkerFactory,
            TaskStateManager stateManager) {
        return new DeploymentApplicationService(
                deploymentPlanCreator,
                planDomainService,
                taskDomainService,
                conflictManager,
                taskWorkerFactory,
                stateManager
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

