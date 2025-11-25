package xyz.firestige.deploy.infrastructure.execution.stage.factory.assembler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.MediaRoutingConfig;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.infrastructure.discovery.SelectionStrategy;
import xyz.firestige.deploy.infrastructure.execution.stage.ConfigurableServiceStage;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.execution.stage.asbc.ASBCResponse;
import xyz.firestige.deploy.infrastructure.execution.stage.asbc.ASBCResponseData;
import xyz.firestige.deploy.infrastructure.execution.stage.asbc.ASBCResultItem;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.SharedStageResources;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.StageAssembler;
import xyz.firestige.deploy.infrastructure.execution.stage.http.HttpResponseData;
import xyz.firestige.deploy.infrastructure.execution.stage.preparer.DataPreparer;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.HttpRequestStep;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ResultValidator;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ValidationResult;

import java.util.*;

/**
 * ASBC Gateway Stage 组装器
 *
 * @since RF-19-06
 */
@Component
@Order(10)
public class AsbcStageAssembler implements StageAssembler {

    private static final Logger log = LoggerFactory.getLogger(AsbcStageAssembler.class);

    @Override
    public String stageName() {
        return "asbc-gateway";
    }

    @Override
    public boolean supports(TenantConfig cfg) {
        return cfg.getMediaRoutingConfig() != null;
    }

    @Override
    public TaskStage buildStage(TenantConfig cfg, SharedStageResources resources) {
        ConfigurableServiceStage.StepConfig stepConfig = ConfigurableServiceStage.StepConfig.builder()
            .stepName("asbc-http-request")
            .dataPreparer(createASBCDataPreparer(cfg, resources))
            .step(new HttpRequestStep(resources.getRestTemplate()))
            .resultValidator(createASBCResultValidator())
            .build();

        return new ConfigurableServiceStage(stageName(), Collections.singletonList(stepConfig));
    }

    /**
     * ASBC 数据准备器
     */
    private DataPreparer createASBCDataPreparer(TenantConfig tenantConfig, SharedStageResources resources) {
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

            // 2. 从 Nacos 获取 endpoint（使用 RANDOM 策略选择单实例）
            String namespace = tenantConfig.getNacosNameSpace();
            java.util.List<String> instances = resources.getServiceDiscoveryHelper()
                .selectInstances("asbcService", namespace, SelectionStrategy.RANDOM, false);

            if (instances.isEmpty()) {
                throw new IllegalStateException("No ASBC service instance available");
            }

            String instance = instances.get(0);  // RANDOM 策略返回单实例
            String endpoint = "https://" + instance + "/api/sbc/traffic-switch";

            // 3. 构建请求数据
            Map<String, Object> body = new HashMap<>();
            body.put("calledNumberMatch", calledNumberList);
            body.put("targetTrunkGroupName", mediaRouting.trunkGroup());

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            // 4. 从 auth 配置读取认证信息
            var authConfig = resources.getConfigLoader().getInfrastructure().getAuthConfig("asbc");
            if (authConfig != null && authConfig.isEnabled()) {
                String token = generateToken(authConfig.getTokenProvider());
                if (token != null) {
                    headers.put("Authorization", "Bearer " + token);
                    log.debug("ASBC auth enabled, token provider: {}", authConfig.getTokenProvider());
                }
            } else {
                log.debug("ASBC auth disabled");
            }

            // 5. 放入 TaskRuntimeContext
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

    /**
     * 生成认证 Token
     */
    private String generateToken(String tokenProvider) {
        if (tokenProvider == null) {
            return null;
        }

        switch (tokenProvider.toLowerCase()) {
            case "random":
                return generateRandomHex(32);
            case "oauth2":
                log.warn("OAuth2 token provider not implemented yet");
                return null;
            case "custom":
                log.warn("Custom token provider not implemented yet");
                return null;
            default:
                log.warn("Unknown token provider: {}", tokenProvider);
                return null;
        }
    }

    /**
     * 生成随机 Hex 字符串
     */
    private String generateRandomHex(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString((int) (Math.random() * 16)));
        }
        return sb.toString();
    }
}

