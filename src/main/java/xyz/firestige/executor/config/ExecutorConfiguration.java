package xyz.firestige.executor.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.executor.facade.DeploymentTaskFacade;
import xyz.firestige.executor.facade.DeploymentTaskFacadeImpl;
import xyz.firestige.executor.service.health.HealthCheckClient;
import xyz.firestige.executor.service.health.MockHealthCheckClient;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.validation.validator.BusinessRuleValidator;
import xyz.firestige.executor.validation.validator.ConflictValidator;
import xyz.firestige.executor.validation.validator.NetworkEndpointValidator;
import xyz.firestige.executor.validation.validator.TenantIdValidator;

/**
 * 执行器配置类（新架构接线）
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
    public DeploymentTaskFacade deploymentTaskFacade(
            ValidationChain validationChain,
            TaskStateManager taskStateManager,
            ExecutorProperties executorProperties,
            HealthCheckClient healthCheckClient) {
        return new DeploymentTaskFacadeImpl(validationChain, taskStateManager, executorProperties, healthCheckClient);
    }
}
