package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.execution.stage.AbstractConfigurableStep;

import java.util.Map;
import java.util.Objects;

/**
 * Redis Pub/Sub 广播步骤（可复用）
 * 
 * 配置来源：
 * - YAML: message（固定的消息内容，即 serviceName）
 * - Infrastructure: pubsub-topic（固定的 topic）
 * 
 * 用于：blue-green-gateway, portal
 */
public class MessageBroadcastStep extends AbstractConfigurableStep {
    
    private static final Logger log = LoggerFactory.getLogger(MessageBroadcastStep.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final DeploymentConfigLoader configLoader;
    
    public MessageBroadcastStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig,
            RedisTemplate<String, Object> redisTemplate,
            DeploymentConfigLoader configLoader) {
        
        super(stepName, stepConfig, serviceConfig);
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate cannot be null");
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader cannot be null");
    }
    
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 1. 从 YAML 配置获取 message
        String message = getConfigValue("message", null);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message not configured in YAML");
        }
        
        // 2. 从 Infrastructure 配置获取 topic
        String topic = configLoader.getInfrastructure()
                .getRedis()
                .getPubsubTopic();
        
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException("Redis pubsub-topic not configured in infrastructure");
        }
        
        // 3. 发布消息
        redisTemplate.convertAndSend(topic, message);
        
        log.info("[MessageBroadcastStep] Redis Pub/Sub message sent: topic={}, message={}", 
                topic, message);
    }
}
