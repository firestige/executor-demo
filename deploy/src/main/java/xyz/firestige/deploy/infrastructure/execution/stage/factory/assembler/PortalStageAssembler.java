package xyz.firestige.deploy.infrastructure.execution.stage.factory.assembler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.infrastructure.discovery.SelectionStrategy;
import xyz.firestige.deploy.infrastructure.execution.stage.ConfigurableServiceStage;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.SharedStageResources;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.StageAssembler;
import xyz.firestige.deploy.infrastructure.execution.stage.http.HttpResponseData;
import xyz.firestige.deploy.infrastructure.execution.stage.portal.PortalResponse;
import xyz.firestige.deploy.infrastructure.execution.stage.preparer.DataPreparer;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.HttpRequestStep;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ResultValidator;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ValidationResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Portal Stage 组装器
 *
 * @since RF-19-06
 */
@Component
@Order(20)
public class PortalStageAssembler implements StageAssembler {

    private static final Logger log = LoggerFactory.getLogger(PortalStageAssembler.class);

    @Override
    public String stageName() {
        return "portal";
    }

    @Override
    public boolean supports(TenantConfig cfg) {
        return cfg.getDeployUnit() != null;
    }

    @Override
    public TaskStage buildStage(TenantConfig cfg, SharedStageResources resources) {
        ConfigurableServiceStage.StepConfig stepConfig = ConfigurableServiceStage.StepConfig.builder()
            .stepName("portal-notify")
            .dataPreparer(createPortalDataPreparer(cfg, resources))
            .step(new HttpRequestStep(resources.getRestTemplate()))
            .resultValidator(createPortalResultValidator())
            .build();

        return new ConfigurableServiceStage(stageName(), Collections.singletonList(stepConfig));
    }

    /**
     * Portal 数据准备器
     */
    private DataPreparer createPortalDataPreparer(TenantConfig tenantConfig, SharedStageResources resources) {
        return (ctx) -> {
            // 1. 从 Nacos 获取 endpoint（使用 RANDOM 策略选择单实例）
            String namespace = tenantConfig.getNacosNameSpace();
            java.util.List<String> instances = resources.getServiceDiscoveryHelper()
                .selectInstances("portalService", namespace, SelectionStrategy.RANDOM, false);

            if (instances.isEmpty()) {
                throw new IllegalStateException("No portal service instance available");
            }

            String instance = instances.get(0);  // RANDOM 策略返回单实例
            String endpoint = "http://" + instance + "/icc-agent-portal/inner/v1/notify/bgSwitch";

            // 2. 构建请求 body
            Map<String, Object> body = new HashMap<>();
            body.put("tenantId", tenantConfig.getTenantId().getValue());
            body.put("targetDeployUnit", tenantConfig.getDeployUnit().name());
            body.put("timestamp", String.valueOf(System.currentTimeMillis()));

            // 3. 构建 headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            // 4. 放入 TaskRuntimeContext
            ctx.addVariable("url", endpoint);
            ctx.addVariable("method", "POST");
            ctx.addVariable("headers", headers);
            ctx.addVariable("body", body);

            log.debug("Portal 数据准备完成: endpoint={}, tenantId={}",
                endpoint, tenantConfig.getTenantId().getValue());
        };
    }

    /**
     * Portal 结果验证器
     */
    private ResultValidator createPortalResultValidator() {
        return (ctx) -> {
            HttpResponseData response = ctx.getAdditionalData("httpResponse", HttpResponseData.class);

            // 1. 检查 HTTP 状态码
            if (!response.is2xx()) {
                return ValidationResult.failure(
                    String.format("Portal HTTP 错误: %d", response.getStatusCode())
                );
            }

            // 2. 解析 JSON
            try {
                PortalResponse portalResponse = response.parseBody(PortalResponse.class);

                // 3. 检查业务 code
                if ("0".equals(portalResponse.getCode())) {
                    return ValidationResult.success(
                        String.format("Portal 通知成功: %s", portalResponse.getMsg())
                    );
                } else {
                    return ValidationResult.failure(
                        String.format("Portal 通知失败: code=%s, msg=%s",
                            portalResponse.getCode(), portalResponse.getMsg())
                    );
                }

            } catch (Exception e) {
                log.error("Portal 响应解析失败", e);
                return ValidationResult.failure("响应解析失败: " + e.getMessage());
            }
        };
    }
}

