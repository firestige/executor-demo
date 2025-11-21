package xyz.firestige.deploy.infrastructure.execution.stage.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.application.dto.MediaRoutingConfig;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.infrastructure.execution.stage.ConfigurableServiceStage;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.execution.stage.asbc.ASBCResponse;
import xyz.firestige.deploy.infrastructure.execution.stage.asbc.ASBCResponseData;
import xyz.firestige.deploy.infrastructure.execution.stage.asbc.ASBCResultItem;
import xyz.firestige.deploy.infrastructure.execution.stage.http.HttpResponseData;
import xyz.firestige.deploy.infrastructure.execution.stage.portal.PortalResponse;
import xyz.firestige.deploy.infrastructure.execution.stage.preparer.DataPreparer;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.HttpRequestStep;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ResultValidator;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态 Stage 工厂（代码编排）
 * 根据 TenantConfig 动态创建 Stage 列表
 *
 * @since RF-19 三层抽象架构
 */
@Component
public class DynamicStageFactory {

    private static final Logger log = LoggerFactory.getLogger(DynamicStageFactory.class);

    private final RestTemplate restTemplate;

    public DynamicStageFactory(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 构建 Stage 列表（严格按顺序）
     *
     * @param tenantConfig 租户配置
     * @return Stage 列表
     */
    public List<TaskStage> buildStages(TenantConfig tenantConfig) {
        List<TaskStage> stages = new ArrayList<>();

        log.info("开始构建 Stages for tenant: {}", tenantConfig.getTenantId());

        // Stage 1: ASBC Gateway
        if (tenantConfig.getMediaRoutingConfig() != null) {
            stages.add(createASBCStage(tenantConfig));
            log.debug("添加 ASBC Stage");
        }

        // Stage 2: Portal
        if (tenantConfig.getDeployUnit() != null) {
            stages.add(createPortalStage(tenantConfig));
            log.debug("添加 Portal Stage");
        }

        // TODO: Stage 3: OBService (待实现)
        // TODO: Stage 4: Blue-Green Gateway (已存在)

        log.info("构建完成，共 {} 个 Stage", stages.size());
        return stages;
    }

    // ========================================
    // ASBC Gateway Stage
    // ========================================

    private TaskStage createASBCStage(TenantConfig tenantConfig) {
        ConfigurableServiceStage.StepConfig stepConfig = ConfigurableServiceStage.StepConfig.builder()
            .stepName("asbc-http-request")
            .dataPreparer(createASBCDataPreparer(tenantConfig))
            .step(new HttpRequestStep(restTemplate))
            .resultValidator(createASBCResultValidator())
            .build();

        return new ConfigurableServiceStage("asbc-gateway", Collections.singletonList(stepConfig));
    }

    /**
     * ASBC 数据准备器
     */
    private DataPreparer createASBCDataPreparer(TenantConfig tenantConfig) {
        return (ctx) -> {
            MediaRoutingConfig mediaRouting = tenantConfig.getMediaRoutingConfig();

            // 1. 解析 calledNumberRules (逗号分隔 → List)
            String rulesStr = mediaRouting.calledNumberRules();
            String[] numbers = rulesStr.split(",");
            List<String> calledNumberList = new ArrayList<>();
            for (String num : numbers) {
                String trimmed = num.trim();
                if (!trimmed.isEmpty()) {
                    calledNumberList.add(trimmed);
                }
            }

            // 2. 获取 endpoint (暂时硬编码，TODO: 从 Nacos 获取)
            String endpoint = "https://192.168.1.100:8080/api/sbc/traffic-switch";

            // 3. 构建请求数据
            Map<String, Object> body = new HashMap<>();
            body.put("calledNumberMatch", calledNumberList);
            body.put("targetTrunkGroupName", mediaRouting.trunkGroup());

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            // auth disabled, 不填 Authorization header

            // 4. 放入 TaskRuntimeContext
            ctx.addVariable("url", endpoint);
            ctx.addVariable("method", "POST");
            ctx.addVariable("headers", headers);
            ctx.addVariable("body", body);

            log.debug("ASBC 数据准备完成: endpoint={}, calledNumberMatch={}",
                endpoint, calledNumberList);
        };
    }

    /**
     * ASBC 结果验证器
     */
    private ResultValidator createASBCResultValidator() {
        return (ctx) -> {
            HttpResponseData response = ctx.getAdditionalData("httpResponse", HttpResponseData.class);

            // 1. 检查 HTTP 状态码
            if (!response.is2xx()) {
                return ValidationResult.failure(
                    String.format("ASBC HTTP 错误: %d", response.getStatusCode())
                );
            }

            // 2. 解析 JSON
            try {
                ASBCResponse asbcResponse = response.parseBody(ASBCResponse.class);

                // 3. 检查业务 code
                if (asbcResponse.getCode() == null || asbcResponse.getCode() != 0) {
                    return ValidationResult.failure(
                        String.format("ASBC 返回错误: code=%d, msg=%s",
                            asbcResponse.getCode(), asbcResponse.getMsg())
                    );
                }

                // 4. 检查 failList
                ASBCResponseData data = asbcResponse.getData();
                if (data != null && data.getFailList() != null && !data.getFailList().isEmpty()) {
                    return ValidationResult.failure(buildASBCFailureMessage(data));
                }

                // 5. 全部成功
                int successCount = (data != null && data.getSuccessList() != null)
                    ? data.getSuccessList().size() : 0;
                return ValidationResult.success(
                    String.format("ASBC 配置成功: %d 个规则", successCount)
                );

            } catch (Exception e) {
                log.error("ASBC 响应解析失败", e);
                return ValidationResult.failure("响应解析失败: " + e.getMessage());
            }
        };
    }

    /**
     * 构建 ASBC 失败信息（包含成功和失败详情）
     */
    private String buildASBCFailureMessage(ASBCResponseData data) {
        StringBuilder sb = new StringBuilder("ASBC 配置部分失败:\n");

        // 成功列表
        if (data.getSuccessList() != null && !data.getSuccessList().isEmpty()) {
            sb.append("成功 (").append(data.getSuccessList().size()).append(" 项):\n");
            for (ASBCResultItem item : data.getSuccessList()) {
                sb.append("  ✓ ").append(item.getCalledNumberMatch())
                  .append(" → ").append(item.getTargetTrunkGroupName()).append("\n");
            }
        }

        // 失败列表
        if (data.getFailList() != null && !data.getFailList().isEmpty()) {
            sb.append("失败 (").append(data.getFailList().size()).append(" 项):\n");
            for (ASBCResultItem item : data.getFailList()) {
                sb.append("  ✗ ").append(item.getCalledNumberMatch())
                  .append(" → ").append(item.getTargetTrunkGroupName())
                  .append(" [").append(item.getMsg()).append("]\n");
            }
        }

        return sb.toString();
    }

    // ========================================
    // Portal Stage
    // ========================================

    private TaskStage createPortalStage(TenantConfig tenantConfig) {
        ConfigurableServiceStage.StepConfig stepConfig = ConfigurableServiceStage.StepConfig.builder()
            .stepName("portal-notify")
            .dataPreparer(createPortalDataPreparer(tenantConfig))
            .step(new HttpRequestStep(restTemplate))
            .resultValidator(createPortalResultValidator())
            .build();

        return new ConfigurableServiceStage("portal", Collections.singletonList(stepConfig));
    }

    /**
     * Portal 数据准备器
     */
    private DataPreparer createPortalDataPreparer(TenantConfig tenantConfig) {
        return (ctx) -> {
            // 1. 获取 endpoint (暂时硬编码，TODO: 从 Nacos 获取)
            String baseUrl = "http://192.168.1.20:8080";
            String endpoint = baseUrl + "/icc-agent-portal/inner/v1/notify/bgSwitch";

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

