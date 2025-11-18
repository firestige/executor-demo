package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.domain.stage.config.BlueGreenGatewayConfig;
import xyz.firestige.deploy.domain.stage.config.PortalConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.execution.stage.AbstractConfigurableStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 端点健康检查轮询步骤（可复用）
 * 
 * 配置来源：
 * - YAML: nacos-service-name-key, validation-type, validation-rule, expected-value, retry-policy
 * - Infrastructure: health-check (default-path, interval-seconds, max-attempts), nacos, fallback-instances
 * - ServiceConfig: tenantId, nacosNamespace, healthCheckPath
 * 
 * 特性：
 * - 支持 Nacos 服务发现
 * - 支持降级到固定 IP 列表（当 Nacos 不可用或未配置时）
 * - 支持 JSON Path 验证
 * 
 * 用于：blue-green-gateway, portal
 */
public class EndpointPollingStep extends AbstractConfigurableStep {
    
    private static final Logger log = LoggerFactory.getLogger(EndpointPollingStep.class);
    
    private final Object nacosNamingService;  // NamingService，可选依赖
    private final RestTemplate restTemplate;
    private final DeploymentConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    
    public EndpointPollingStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig,
            Object nacosNamingService,  // 可以为 null（降级模式）
            RestTemplate restTemplate,
            DeploymentConfigLoader configLoader,
            ObjectMapper objectMapper) {
        
        super(stepName, stepConfig, serviceConfig);
        this.nacosNamingService = nacosNamingService;  // 允许为 null
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate cannot be null");
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }
    
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 1. 从 YAML 配置获取 Nacos 服务名 key
        String nacosServiceNameKey = getConfigValue("nacos-service-name-key", null);
        if (nacosServiceNameKey == null) {
            throw new IllegalArgumentException("nacos-service-name-key not configured in YAML");
        }
        
        // 2. 从 ServiceConfig 获取命名空间和健康检查路径
        String namespace = extractNamespace();
        String healthCheckPath = extractHealthCheckPath();
        
        // 3. 获取实例列表（优先 Nacos，降级到固定 IP）
        List<ServiceInstance> instances = getServiceInstances(nacosServiceNameKey, namespace);
        
        if (instances.isEmpty()) {
            throw new IllegalStateException("No available instances for service: " + nacosServiceNameKey);
        }
        
        // 4. 获取重试策略
        int maxAttempts = getRetryMaxAttempts();
        int intervalSeconds = getRetryIntervalSeconds();
        String validationType = getConfigValue("validation-type", "json-path");
        String validationRule = getConfigValue("validation-rule", "$.status");
        String expectedValue = getConfigValue("expected-value", "UP");
        
        log.info("[EndpointPollingStep] Starting health check: instances={}, maxAttempts={}", 
                instances.size(), maxAttempts);
        
        // 5. 轮询所有实例
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean allHealthy = true;
            
            for (ServiceInstance instance : instances) {
                String url = buildHealthCheckUrl(instance, healthCheckPath);
                try {
                    String response = restTemplate.getForObject(url, String.class);
                    boolean valid = validateResponse(response, validationType, validationRule, expectedValue);
                    
                    log.info("[EndpointPollingStep] Attempt {}: instance={}, valid={}", 
                            attempt, instance.address(), valid);
                    
                    if (!valid) {
                        allHealthy = false;
                    }
                } catch (Exception e) {
                    log.warn("[EndpointPollingStep] Attempt {}: instance={}, error={}", 
                            attempt, instance.address(), e.getMessage());
                    allHealthy = false;
                }
            }
            
            if (allHealthy) {
                log.info("[EndpointPollingStep] All instances healthy after {} attempts", attempt);
                return;  // 成功
            }
            
            if (attempt < maxAttempts) {
                Thread.sleep(intervalSeconds * 1000L);
            }
        }
        
        throw new IllegalStateException("Health check failed after " + maxAttempts + " attempts");
    }
    
    /**
     * 获取服务实例列表（支持 Nacos + 降级）
     */
    private List<ServiceInstance> getServiceInstances(String nacosServiceNameKey, String namespace) {
        String nacosServiceName = configLoader.getInfrastructure()
                .getNacos()
                .getServiceName(nacosServiceNameKey);
        
        // 尝试从 Nacos 获取
        if (nacosNamingService != null && nacosServiceName != null) {
            try {
                List<ServiceInstance> nacosInstances = queryFromNacos(nacosServiceName, namespace);
                if (nacosInstances != null && !nacosInstances.isEmpty()) {
                    log.info("[EndpointPollingStep] Using Nacos service discovery: service={}, count={}", 
                            nacosServiceName, nacosInstances.size());
                    return nacosInstances;
                }
            } catch (Exception e) {
                log.warn("[EndpointPollingStep] Nacos query failed, falling back to fixed instances: {}", 
                        e.getMessage());
            }
        }
        
        // 降级到固定 IP 列表
        String serviceType = serviceConfig.getServiceType();
        List<String> fallbackAddresses = configLoader.getInfrastructure()
                .getFallbackInstances()
                .get(serviceType);
        
        if (fallbackAddresses == null || fallbackAddresses.isEmpty()) {
            log.error("[EndpointPollingStep] No fallback instances configured for: {}", serviceType);
            return List.of();
        }
        
        log.info("[EndpointPollingStep] Using fallback instances: service={}, count={}", 
                serviceType, fallbackAddresses.size());
        
        return fallbackAddresses.stream()
                .map(ServiceInstance::fromAddress)
                .collect(Collectors.toList());
    }
    
    /**
     * 从 Nacos 查询实例（使用反射避免编译时依赖）
     */
    private List<ServiceInstance> queryFromNacos(String serviceName, String namespace) {
        if (nacosNamingService == null) {
            return null;
        }
        
        try {
            // 使用反射调用: List<Instance> selectInstances(String serviceName, String groupName, boolean healthy)
            var method = nacosNamingService.getClass().getMethod(
                    "selectInstances", String.class, String.class, boolean.class);
            
            @SuppressWarnings("unchecked")
            List<Object> nacosInstances = (List<Object>) method.invoke(
                    nacosNamingService, serviceName, namespace, true);
            
            if (nacosInstances == null || nacosInstances.isEmpty()) {
                return null;
            }
            
            // 转换为内部实例类型
            List<ServiceInstance> instances = new ArrayList<>();
            for (Object nacosInstance : nacosInstances) {
                String ip = (String) nacosInstance.getClass().getMethod("getIp").invoke(nacosInstance);
                int port = (int) nacosInstance.getClass().getMethod("getPort").invoke(nacosInstance);
                instances.add(new ServiceInstance(ip, port));
            }
            
            return instances;
        } catch (Exception e) {
            log.warn("[EndpointPollingStep] Nacos reflection call failed: {}", e.getMessage());
            return null;
        }
    }
    
    private String extractNamespace() {
        if (serviceConfig instanceof BlueGreenGatewayConfig bgConfig) {
            return bgConfig.getNacosNamespace();
        } else if (serviceConfig instanceof PortalConfig portalConfig) {
            return portalConfig.getNacosNamespace();
        }
        return "public";
    }
    
    private String extractHealthCheckPath() {
        if (serviceConfig instanceof BlueGreenGatewayConfig bgConfig) {
            return bgConfig.getHealthCheckPath();
        } else if (serviceConfig instanceof PortalConfig portalConfig) {
            return portalConfig.getHealthCheckPath();
        }
        return configLoader.getInfrastructure().getHealthCheck().getDefaultPath();
    }
    
    private int getRetryMaxAttempts() {
        // 优先从步骤配置获取，其次从 Infrastructure 获取
        if (hasConfig("retry-policy")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> retryPolicy = (Map<String, Object>) stepConfig.get("retry-policy");
            if (retryPolicy != null && retryPolicy.containsKey("max-attempts")) {
                return ((Number) retryPolicy.get("max-attempts")).intValue();
            }
        }
        return configLoader.getInfrastructure().getHealthCheck().getMaxAttempts();
    }
    
    private int getRetryIntervalSeconds() {
        if (hasConfig("retry-policy")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> retryPolicy = (Map<String, Object>) stepConfig.get("retry-policy");
            if (retryPolicy != null && retryPolicy.containsKey("interval-seconds")) {
                return ((Number) retryPolicy.get("interval-seconds")).intValue();
            }
        }
        return configLoader.getInfrastructure().getHealthCheck().getIntervalSeconds();
    }
    
    private String buildHealthCheckUrl(ServiceInstance instance, String path) {
        return String.format("http://%s:%d%s", instance.ip(), instance.port(), path);
    }
    
    private boolean validateResponse(String response, String validationType, String rule, String expectedValue) {
        if ("json-path".equals(validationType)) {
            try {
                JsonNode jsonNode = objectMapper.readTree(response);
                JsonNode valueNode = jsonNode.at("/" + rule.replace("$.", "").replace(".", "/"));
                
                if (valueNode.isMissingNode()) {
                    return false;
                }
                
                String actualValue = valueNode.asText();
                return expectedValue.equals(actualValue);
            } catch (Exception e) {
                log.warn("[EndpointPollingStep] JSON validation failed: {}", e.getMessage());
                return false;
            }
        }
        return true;
    }
    
    /**
     * 服务实例记录
     */
    private record ServiceInstance(String ip, int port) {
        String address() {
            return ip + ":" + port;
        }
        
        static ServiceInstance fromAddress(String address) {
            String[] parts = address.split(":");
            String ip = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 8080;
            return new ServiceInstance(ip, port);
        }
    }
}
