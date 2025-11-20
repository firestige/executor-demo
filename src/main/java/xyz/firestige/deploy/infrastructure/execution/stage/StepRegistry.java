package xyz.firestige.deploy.infrastructure.execution.stage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.config.model.StepDefinition;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.ASBCConfigRequestStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.EndpointPollingStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.KeyValueWriteStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.MessageBroadcastStep;
import xyz.firestige.deploy.infrastructure.template.TemplateResolver;
import xyz.firestige.deploy.infrastructure.template.VariableContextBuilder;

import java.util.Map;

/**
 * 步骤注册表（工厂 + 依赖注入）
 * 
 * 职责：
 * 1. 注册所有可用的步骤类型
 * 2. 基于配置创建步骤实例
 * 3. 注入必要的依赖（Redis, Nacos, RestTemplate 等）
 */
@Component
public class StepRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(StepRegistry.class);
    
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final DeploymentConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    private final Object nacosNamingService;  // Optional Nacos dependency
    private final TemplateResolver templateResolver;
    private final VariableContextBuilder variableContextBuilder;

    public StepRegistry(
            StringRedisTemplate redisTemplate,
            RestTemplate restTemplate,
            DeploymentConfigLoader configLoader,
            ObjectMapper objectMapper,
            TemplateResolver templateResolver,
            VariableContextBuilder variableContextBuilder,
            @Autowired(required = false) Object nacosNamingService) {
        
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
        this.templateResolver = templateResolver;
        this.variableContextBuilder = variableContextBuilder;
        this.nacosNamingService = nacosNamingService;
        
        if (nacosNamingService == null) {
            log.warn("Nacos NamingService not configured, will use fallback instances for service discovery");
        } else {
            log.info("Nacos NamingService configured: {}", nacosNamingService.getClass().getName());
        }
    }
    
    /**
     * 基于步骤定义创建步骤实例
     */
    public StageStep createStep(StepDefinition stepDef, ServiceConfig serviceConfig) {
        String stepType = stepDef.getType();
        String stepName = stepType + "-" + System.currentTimeMillis();  // 唯一名称

        // 构建变量上下文
        Map<String, String> variables = variableContextBuilder.buildContext(serviceConfig);

        // 解析配置中的模板变量（委托给 TemplateResolver）
        @SuppressWarnings("unchecked")
        Map<String, Object> resolvedConfig = (Map<String, Object>) templateResolver.resolve(
            stepDef.getConfig(),
            variables
        );

        log.debug("Creating step: type={}, serviceType={}, resolvedConfig={}",
                stepType, serviceConfig.getServiceType(), resolvedConfig);

        return switch (stepType) {
            case "key-value-write" -> new KeyValueWriteStep(
                stepName, resolvedConfig, serviceConfig, redisTemplate, objectMapper, configLoader);

            case "message-broadcast" -> new MessageBroadcastStep(
                stepName, resolvedConfig, serviceConfig, redisTemplate, configLoader);

            case "endpoint-polling" -> new EndpointPollingStep(
                stepName, resolvedConfig, serviceConfig, nacosNamingService, restTemplate, configLoader, objectMapper);

            case "asbc-config-request" -> new ASBCConfigRequestStep(
                stepName, resolvedConfig, serviceConfig, restTemplate, configLoader, objectMapper);

            default -> throw new UnsupportedOperationException("Unknown step type: " + stepType);
        };
    }
}
