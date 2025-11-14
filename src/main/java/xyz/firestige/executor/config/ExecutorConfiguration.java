package xyz.firestige.executor.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.executor.execution.pipeline.CheckpointManager;
import xyz.firestige.executor.execution.pipeline.InMemoryCheckpointManager;
import xyz.firestige.executor.execution.pipeline.Pipeline;
import xyz.firestige.executor.execution.pipeline.PipelineStage;
import xyz.firestige.executor.facade.DeploymentTaskFacade;
import xyz.firestige.executor.facade.DeploymentTaskFacadeImpl;
import xyz.firestige.executor.orchestration.ExecutionUnitScheduler;
import xyz.firestige.executor.orchestration.TaskOrchestrator;
import xyz.firestige.executor.service.registry.ServiceRegistry;
import xyz.firestige.executor.service.stage.ServiceNotificationStage;
import xyz.firestige.executor.service.strategy.DirectRpcNotificationStrategy;
import xyz.firestige.executor.service.strategy.RedisRpcNotificationStrategy;
import xyz.firestige.executor.service.strategy.ServiceNotificationStrategy;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.validation.ConfigValidator;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.validation.validator.BusinessRuleValidator;
import xyz.firestige.executor.validation.validator.ConflictValidator;
import xyz.firestige.executor.validation.validator.NetworkEndpointValidator;
import xyz.firestige.executor.validation.validator.TenantIdValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行器配置类
 * 配置所有 Bean
 */
@Configuration
public class ExecutorConfiguration {

    /**
     * 配置检查点管理器
     */
    @Bean
    public CheckpointManager checkpointManager() {
        return new InMemoryCheckpointManager();
    }

    /**
     * 配置状态管理器
     */
    @Bean
    public TaskStateManager taskStateManager(ApplicationEventPublisher eventPublisher) {
        return new TaskStateManager(eventPublisher);
    }

    /**
     * 配置服务注册中心
     */
    @Bean
    public ServiceRegistry serviceRegistry() {
        ServiceRegistry registry = new ServiceRegistry();

        // 注册示例服务策略
        registry.registerStrategy(new DirectRpcNotificationStrategy("ServiceA"));
        registry.registerStrategy(new DirectRpcNotificationStrategy("ServiceB"));
        registry.registerStrategy(new RedisRpcNotificationStrategy("ServiceC"));
        registry.registerStrategy(new RedisRpcNotificationStrategy("ServiceD"));

        return registry;
    }

    /**
     * 配置 Pipeline（通过工厂方法）
     */
    @Bean
    public ExecutionUnitScheduler.PipelineFactory pipelineFactory(
            CheckpointManager checkpointManager,
            ServiceRegistry serviceRegistry) {

        return () -> {
            Pipeline pipeline = new Pipeline();
            pipeline.setCheckpointManager(checkpointManager);

            // 添加 Stage（从服务注册中心获取）
            List<PipelineStage> stages = createStages(serviceRegistry);
            pipeline.setStages(stages);

            return pipeline;
        };
    }

    /**
     * 创建 Pipeline Stage 列表
     */
    private List<PipelineStage> createStages(ServiceRegistry serviceRegistry) {
        List<PipelineStage> stages = new ArrayList<>();

        // 从服务注册中心获取所有策略并创建对应的 Stage
        List<ServiceNotificationStrategy> strategies = serviceRegistry.getAllStrategies();

        int order = 10;
        for (ServiceNotificationStrategy strategy : strategies) {
            ServiceNotificationStage stage = new ServiceNotificationStage(strategy, order);
            stages.add(stage);
            order += 10;
        }

        return stages;
    }

    /**
     * 配置执行单调度器
     */
    @Bean
    public ExecutionUnitScheduler executionUnitScheduler(
            ExecutionUnitScheduler.PipelineFactory pipelineFactory,
            TaskStateManager taskStateManager) {

        return new ExecutionUnitScheduler(10, pipelineFactory, taskStateManager);
    }

    /**
     * 配置任务编排器
     */
    @Bean
    public TaskOrchestrator taskOrchestrator(
            ExecutionUnitScheduler executionUnitScheduler,
            TaskStateManager taskStateManager) {

        return new TaskOrchestrator(executionUnitScheduler, taskStateManager);
    }

    /**
     * 配置校验链
     */
    @Bean
    public ValidationChain validationChain() {
        ValidationChain chain = new ValidationChain(false); // 不使用快速失败

        // 添加校验器（按优先级顺序）
        chain.addValidator(new TenantIdValidator());
        chain.addValidator(new NetworkEndpointValidator());
        chain.addValidator(new ConflictValidator());
        chain.addValidator(new BusinessRuleValidator());

        return chain;
    }

    /**
     * 配置 Facade
     */
    @Bean
    public DeploymentTaskFacade deploymentTaskFacade(
            ValidationChain validationChain,
            TaskOrchestrator taskOrchestrator,
            TaskStateManager taskStateManager) {

        return new DeploymentTaskFacadeImpl(validationChain, taskOrchestrator, taskStateManager);
    }
}

