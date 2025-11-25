package xyz.firestige.deploy.infrastructure.execution.stage.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.discovery.ServiceDiscoveryHelper;
import xyz.firestige.redis.ack.api.RedisAckService;
import xyz.firestige.service.AgentService;

import java.util.Objects;

/**
 * 共享 Stage 资源聚合器
 * 职责：
 * - 聚合所有 StageAssembler 需要的基础设施依赖
 * - 提供不可变的 getter 访问
 * - 启动时校验必需依赖非空
 *
 * 不应包含：
 * - 任何业务逻辑方法（如 extractSourceUnit, generateToken 等）
 * - 可变状态
 *
 * @since RF-19-06 策略化重构
 */
@Component
public class SharedStageResources {

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final DeploymentConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    private final AgentService agentService;  // 可选，OBService 使用
    private final RedisAckService redisAckService;
    private final ServiceDiscoveryHelper serviceDiscoveryHelper;

    @Autowired
    public SharedStageResources(
            RestTemplate restTemplate,
            StringRedisTemplate redisTemplate,
            DeploymentConfigLoader configLoader,
            ObjectMapper objectMapper,
            @Autowired(required = false) AgentService agentService,
            RedisAckService redisAckService,
            ServiceDiscoveryHelper serviceDiscoveryHelper) {

        // 启动校验必需依赖
        Objects.requireNonNull(restTemplate, "RestTemplate cannot be null");
        Objects.requireNonNull(redisTemplate, "StringRedisTemplate cannot be null");
        Objects.requireNonNull(configLoader, "DeploymentConfigLoader cannot be null");
        Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        Objects.requireNonNull(redisAckService, "RedisAckService cannot be null");
        Objects.requireNonNull(serviceDiscoveryHelper, "ServiceDiscoveryHelper cannot be null");
        // agentService 可选（OBService 有降级逻辑）

        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
        this.agentService = agentService;
        this.redisAckService = redisAckService;
        this.serviceDiscoveryHelper = serviceDiscoveryHelper;
    }

    // 只提供 getter，无任何业务方法

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public StringRedisTemplate getRedisTemplate() {
        return redisTemplate;
    }

    public DeploymentConfigLoader getConfigLoader() {
        return configLoader;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * 获取 AgentService（可选依赖）
     * @return AgentService 实例，可能为 null
     */
    public AgentService getAgentService() {
        return agentService;
    }

    /**
     * 获取 RedisAckService
     * @return RedisAckService 实例
     */
    public RedisAckService getRedisAckService() {
        return redisAckService;
    }

    /**
     * 获取 ServiceDiscoveryHelper
     * @return ServiceDiscoveryHelper 实例
     */
    public ServiceDiscoveryHelper getServiceDiscoveryHelper() {
        return serviceDiscoveryHelper;
    }
}

