package xyz.firestige.deploy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import xyz.firestige.deploy.config.properties.ExecutorProperties;
import xyz.firestige.deploy.config.properties.InfrastructureProperties;
import xyz.firestige.deploy.application.conflict.TenantConflictCoordinator;
import xyz.firestige.deploy.application.facade.PlanExecutionFacade;
import xyz.firestige.deploy.application.lifecycle.PlanLifecycleService;
import xyz.firestige.deploy.application.orchestration.TaskExecutionOrchestrator;
import xyz.firestige.deploy.application.plan.DeploymentPlanCreator;
import xyz.firestige.deploy.application.task.TaskOperationService;
import xyz.firestige.deploy.application.validation.ConflictValidator;
import xyz.firestige.deploy.application.validation.BusinessValidator;
import xyz.firestige.deploy.domain.plan.PlanDomainService;
import xyz.firestige.deploy.domain.plan.PlanRepository;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.TaskRepository;
import xyz.firestige.deploy.domain.task.TaskRuntimeRepository;
import xyz.firestige.deploy.facade.DeploymentTaskFacade;
import xyz.firestige.deploy.facade.converter.TenantConfigConverter;
import xyz.firestige.deploy.infrastructure.execution.DefaultTaskWorkerFactory;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.StageFactory;
import xyz.firestige.deploy.infrastructure.persistence.plan.InMemoryPlanRepository;
import xyz.firestige.deploy.infrastructure.persistence.task.InMemoryTaskRepository;
import xyz.firestige.deploy.infrastructure.persistence.task.InMemoryTaskRuntimeRepository;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;
import xyz.firestige.deploy.infrastructure.validation.ValidationChain;
import xyz.firestige.deploy.infrastructure.validation.validator.BusinessRuleValidator;
import xyz.firestige.deploy.infrastructure.validation.validator.NetworkEndpointValidator;
import xyz.firestige.deploy.infrastructure.validation.validator.TenantIdValidator;
import xyz.firestige.deploy.infrastructure.event.SpringDomainEventPublisher;

/**
 * 执行器配置类（DDD 重构版）
 * <p>
 * 重构说明：
 * - 新增 Repository Bean (InMemoryPlanRepository, InMemoryTaskRepository)
 * - 新增 DomainService Bean (PlanDomainService, TaskDomainService)
 * - 新增 ApplicationService Bean (DeploymentApplicationService)
 *
 * @since DDD 重构 Phase 2.2.5
 * @updated T-027 启用 @ConfigurationProperties（InfrastructureProperties, ExecutorProperties）
 */
@Configuration
@EnableConfigurationProperties({ExecutorProperties.class, InfrastructureProperties.class})
public class ExecutorConfiguration {

    // ========== 基础设施 Bean ==========


    // ExecutorProperties is now auto-configured via @EnableConfigurationProperties
    // No manual bean creation needed


    @Bean
    public TenantConflictManager conflictManager() {
        // TODO 按配置来，不要写死
        return new TenantConflictManager(TenantConflictManager.ConflictPolicy.FINE_GRAINED);
    }

    /**
     * RF-18: TaskWorkerFactory Bean（方案C架构）
     * 
     * <p>T-033: 移除 StateTransitionService，状态转换由聚合根保护
     * <p>T-035: 移除 CheckpointService，无状态执行器
     */
    @Bean
    public TaskWorkerFactory taskWorkerFactory(
            TaskDomainService taskDomainService,
            ApplicationEventPublisher applicationEventPublisher,
            TenantConflictManager conflictManager,
            ExecutorProperties executorProperties) {
        return new DefaultTaskWorkerFactory(
                taskDomainService,
                applicationEventPublisher,
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

    /**
     * RF-18: TaskDomainService Bean（方案C架构）
     * 
     * <p>T-033: 移除 StateTransitionService，状态转换由聚合根保护
     */
    @Bean
    public TaskDomainService taskDomainService(
            TaskRepository taskRepository,
            TaskRuntimeRepository taskRuntimeRepository,
            DomainEventPublisher domainEventPublisher,
            StageFactory stageFactory) {  // T-028: 新增依赖用于回滚时重新装配 Stages
        return new TaskDomainService(
                taskRepository,
                taskRuntimeRepository,
                domainEventPublisher,
                stageFactory
        );
    }

    // ========== Application Service Bean (DDD 重构新增) ==========



    @Bean
    public DeploymentPlanCreator deploymentPlanCreator(
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            StageFactory stageFactory,
            BusinessValidator businessValidator,
            ExecutorProperties executorProperties,
            TaskRuntimeRepository taskRuntimeRepository) {
        return new DeploymentPlanCreator(
                planDomainService,
                taskDomainService,
                stageFactory,
                businessValidator,
                executorProperties,
                taskRuntimeRepository
        );
    }

    @Bean
    public TenantConfigConverter tenantConfigConverter(ExecutorProperties executorProperties) {
        return new TenantConfigConverter(executorProperties);
    }

    /**
     * RF-20: 拆分后的应用服务 Bean
     */
    
    @Bean
    public PlanLifecycleService planLifecycleService(
            DeploymentPlanCreator deploymentPlanCreator,
            PlanDomainService planDomainService) {
        return new PlanLifecycleService(
                deploymentPlanCreator,
                planDomainService
        );
    }

    @Bean
    public TaskExecutionOrchestrator planExecutionOrchestrator(
            TaskWorkerFactory taskWorkerFactory,
            TaskRuntimeRepository taskRuntimeRepository,
            ExecutorProperties executorProperties) {
        return new TaskExecutionOrchestrator(
                taskWorkerFactory,
                taskRuntimeRepository,
                executorProperties
        );
    }

    @Bean
    public TaskOperationService taskOperationService(
            TaskDomainService taskDomainService,
            TaskRepository taskRepository,
            TaskWorkerFactory taskWorkerFactory) {
        return new TaskOperationService(
                taskDomainService,
                taskRepository,
                taskWorkerFactory
        );
    }

    @Bean
    public TenantConflictCoordinator tenantConflictCoordinator(
            TenantConflictManager conflictManager) {
        return new TenantConflictCoordinator(conflictManager);
    }

    @Bean
    public PlanExecutionFacade planExecutionFacade(
            PlanLifecycleService planLifecycleService,
            TaskExecutionOrchestrator planExecutionOrchestrator,
            TenantConflictCoordinator tenantConflictCoordinator,
            TaskOperationService taskOperationService) {
        return new PlanExecutionFacade(
                planLifecycleService,
                planExecutionOrchestrator,
                tenantConflictCoordinator,
                taskOperationService
        );
    }

    // ========== Facade Bean ==========

    // T-035: 移除 TaskQueryService，调用方自行维护投影
    @Bean
    public DeploymentTaskFacade deploymentTaskFacade(
            PlanLifecycleService planLifecycleService,
            TaskOperationService taskOperationService,
            TenantConfigConverter tenantConfigConverter,
            Validator validator) {
        return new DeploymentTaskFacade(
                planLifecycleService,
                taskOperationService,
                tenantConfigConverter,
                validator);
    }
}
