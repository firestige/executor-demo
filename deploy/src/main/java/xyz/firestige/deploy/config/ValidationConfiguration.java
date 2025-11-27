package xyz.firestige.deploy.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.deploy.application.validation.BusinessValidator;
import xyz.firestige.deploy.application.validation.ConflictValidator;
import xyz.firestige.deploy.infrastructure.validation.ValidationChain;
import xyz.firestige.deploy.infrastructure.validation.validator.BusinessRuleValidator;
import xyz.firestige.deploy.infrastructure.validation.validator.NetworkEndpointValidator;
import xyz.firestige.deploy.infrastructure.validation.validator.TenantIdValidator;

/**
 * 验证配置
 */
@Configuration
public class ValidationConfiguration {
    @Bean
    public Validator validator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        return factory.getValidator();
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
    public BusinessValidator businessValidator() { return new BusinessValidator(); }
}
