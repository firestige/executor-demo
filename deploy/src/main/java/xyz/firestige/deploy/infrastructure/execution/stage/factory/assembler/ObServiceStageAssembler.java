package xyz.firestige.deploy.infrastructure.execution.stage.factory.assembler;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.infrastructure.execution.stage.ConfigurableServiceStage;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.SharedStageResources;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.StageAssembler;
import xyz.firestige.deploy.infrastructure.execution.stage.preparer.DataPreparer;
import xyz.firestige.deploy.infrastructure.execution.stage.redis.ConfigWriteResult;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.ConfigWriteStep;
import xyz.firestige.deploy.infrastructure.execution.stage.steps.PollingStep;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ResultValidator;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ValidationResult;
import xyz.firestige.deploy.domain.stage.model.ObConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * OBService Stage 组装器
 *
 * @since RF-19-06
 */
@Component
@Order(40)
public class ObServiceStageAssembler implements StageAssembler {

    private static final Logger log = LoggerFactory.getLogger(ObServiceStageAssembler.class);

    @Override
    public String stageName() {
        return "ob-service";
    }

    @Override
    public boolean supports(TenantConfig cfg) {
        return cfg.getDeployUnit() != null;
    }

    @Override
    public TaskStage buildStage(TenantConfig cfg, SharedStageResources resources) {
        List<ConfigurableServiceStage.StepConfig> stepConfigs = new ArrayList<>();

        // Step 1: Polling (轮询 AgentService.judgeAgent)
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("ob-agent-polling")
            .dataPreparer(createOBPollingDataPreparer(cfg, resources))
            .step(new PollingStep("ob-agent-polling"))
            .resultValidator(createOBPollingValidator())
            .build());

        // Step 2: ConfigWrite (写入 ObConfig 到 Redis)
        stepConfigs.add(ConfigurableServiceStage.StepConfig.builder()
            .stepName("ob-config-write")
            .dataPreparer(createOBConfigWriteDataPreparer(cfg, resources))
            .step(new ConfigWriteStep(resources.getRedisTemplate()))
            .resultValidator(createOBConfigWriteValidator())
            .build());

        return new ConfigurableServiceStage(stageName(), stepConfigs);
    }

    // ---- Polling ----

    private DataPreparer createOBPollingDataPreparer(TenantConfig config, SharedStageResources resources) {
        return (ctx) -> {
            int intervalMs = resources.getConfigLoader().getInfrastructure().getHealthCheck().getIntervalSeconds() * 1000;
            int maxAttempts = resources.getConfigLoader().getInfrastructure().getHealthCheck().getMaxAttempts();

            ctx.addVariable("pollInterval", intervalMs);
            ctx.addVariable("pollMaxAttempts", maxAttempts);

            ctx.addVariable("pollCondition", (PollingStep.PollCondition) (pollCtx) -> {
                Object agentService = pollCtx.getAdditionalData("agentService");
                if (agentService == null) {
                    log.warn("AgentService 未注入，OB 轮询跳过");
                    return true;
                }

                try {
                    var method = agentService.getClass().getMethod("judgeAgent", String.class, Long.class);
                    Boolean result = (Boolean) method.invoke(
                        agentService,
                        config.getTenantId().getValue(),
                        config.getPlanId().getValue()
                    );

                    log.debug("AgentService.judgeAgent() 返回: {}", result);
                    return result != null && result;

                } catch (Exception e) {
                    log.warn("调用 AgentService.judgeAgent() 异常: {}", e.getMessage());
                    return false;
                }
            });

            log.debug("OB Polling 数据准备完成: interval={}ms, maxAttempts={}", intervalMs, maxAttempts);
        };
    }

    private ResultValidator createOBPollingValidator() {
        return (ctx) -> {
            Boolean isReady = ctx.getAdditionalData("pollingResult", Boolean.class);
            if (isReady != null && isReady) {
                return ValidationResult.success("Agent 就绪");
            }
            return ValidationResult.failure("Agent 轮询超时");
        };
    }

    // ---- ConfigWrite ----

    private DataPreparer createOBConfigWriteDataPreparer(TenantConfig config, SharedStageResources resources) {
        return (ctx) -> {
            String redisKeyPrefix = resources.getConfigLoader().getInfrastructure().getRedis().getHashKeyPrefix();
            String key = redisKeyPrefix + config.getTenantId().getValue();
            String field = "ob-campaign";

            ObConfig obConfig = new ObConfig(
                config.getTenantId().getValue(),
                extractSourceUnit(config),
                extractTargetUnit(config)
            );

            String value;
            try {
                value = resources.getObjectMapper().writeValueAsString(obConfig);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize ObConfig", e);
            }

            ctx.addVariable("key", key);
            ctx.addVariable("field", field);
            ctx.addVariable("value", value);

            log.debug("OB ConfigWrite 数据准备完成: key={}, field={}", key, field);
        };
    }

    private ResultValidator createOBConfigWriteValidator() {
        return (ctx) -> {
            ConfigWriteResult result = ctx.getAdditionalData("configWriteResult", ConfigWriteResult.class);
            if (result != null && result.isSuccess()) {
                return ValidationResult.success("OB 配置写入成功");
            }
            return ValidationResult.failure("OB 配置写入失败");
        };
    }

    // ---- 辅助方法 ----

    private String extractSourceUnit(TenantConfig config) {
        if (config.getPreviousConfig() != null && config.getPreviousConfig().getDeployUnit() != null) {
            return config.getPreviousConfig().getDeployUnit().name();
        }
        return extractTargetUnit(config);
    }

    private String extractTargetUnit(TenantConfig config) {
        if (config.getDeployUnit() != null) {
            return config.getDeployUnit().name();
        }
        throw new IllegalArgumentException("deployUnit.name is required");
    }
}

