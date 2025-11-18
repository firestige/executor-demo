package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.domain.stage.config.ASBCGatewayConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.execution.stage.AbstractConfigurableStep;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ASBC 配置请求步骤（独立实现）
 * 
 * 配置来源：
 * - YAML: http-method, validation-type, expected-status
 * - Infrastructure: asbc.fixed-instances, asbc.config-endpoint
 * - ServiceConfig: ASBCGatewayConfig (tenantId, configVersion, mediaRouting)
 * 
 * 用于：asbc-gateway
 */
public class ASBCConfigRequestStep extends AbstractConfigurableStep {
    
    private static final Logger log = LoggerFactory.getLogger(ASBCConfigRequestStep.class);
    
    private final RestTemplate restTemplate;
    private final DeploymentConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    
    public ASBCConfigRequestStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig,
            RestTemplate restTemplate,
            DeploymentConfigLoader configLoader,
            ObjectMapper objectMapper) {
        
        super(stepName, stepConfig, serviceConfig);
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate cannot be null");
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        
        // 类型检查
        if (!(serviceConfig instanceof ASBCGatewayConfig)) {
            throw new IllegalArgumentException("ASBCConfigRequestStep requires ASBCGatewayConfig");
        }
    }
    
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        ASBCGatewayConfig asbcConfig = (ASBCGatewayConfig) serviceConfig;
        
        // 1. 从 Infrastructure 配置获取固定实例列表和端点
        List<String> instances = configLoader.getInfrastructure()
                .getAsbc()
                .getFixedInstances();
        
        String endpointPath = configLoader.getInfrastructure()
                .getAsbc()
                .getConfigEndpoint();
        
        if (instances == null || instances.isEmpty()) {
            throw new IllegalStateException("ASBC fixed instances not configured");
        }
        
        // 2. 构建请求数据（ASBC 自定义数据结构）
        Map<String, Object> requestBody = Map.of(
            "tenantId", asbcConfig.getTenantId(),
            "version", asbcConfig.getConfigVersion(),
            "mediaRouting", Map.of(
                "trunkGroup", asbcConfig.getMediaRouting().trunkGroup(),
                "calledNumberRules", asbcConfig.getMediaRouting().calledNumberRules()
            )
        );
        
        // 3. 获取期望的 HTTP 状态码
        int expectedStatus = getConfigInt("expected-status", 200);
        
        // 4. 向所有固定实例发送 POST 请求（不重试）
        for (String instance : instances) {
            String url = "http://" + instance + endpointPath;
            
            try {
                var response = restTemplate.postForEntity(url, requestBody, String.class);
                
                // 验证响应
                if (response.getStatusCode().value() == expectedStatus) {
                    log.info("[ASBCConfigRequestStep] Success: instance={}, status={}", 
                            instance, response.getStatusCode());
                } else {
                    throw new IllegalStateException(
                            "ASBC request failed: expected=" + expectedStatus + 
                            ", actual=" + response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("[ASBCConfigRequestStep] Failed: instance={}, error={}", 
                        instance, e.getMessage());
                throw e;  // ASBC 不支持重试，直接失败
            }
        }
        
        log.info("[ASBCConfigRequestStep] All instances configured successfully");
    }
}
