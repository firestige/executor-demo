package xyz.firestige.deploy.infrastructure.execution.stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.StageResult;
import xyz.firestige.deploy.infrastructure.execution.stage.preparer.DataPreparer;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ResultValidator;
import xyz.firestige.deploy.infrastructure.execution.stage.validator.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 可配置的服务 Stage
 * 通过代码编排 DataPreparer + Step + ResultValidator
 *
 * <p>执行流程：
 * <pre>
 * for each StepConfig:
 *   1. DataPreparer.prepare() - 准备数据
 *   2. Step.execute() - 执行动作
 *   3. ResultValidator.validate() - 验证结果
 * </pre>
 *
 * <p>特点：
 * <ul>
 *   <li>支持多 Step 编排</li>
 *   <li>严格按顺序执行</li>
 *   <li>任一 Step 失败即停止</li>
 *   <li>业务逻辑在 Preparer 和 Validator</li>
 *   <li>Step 只做技术动作</li>
 * </ul>
 *
 * @since RF-19 三层抽象架构
 */
public class ConfigurableServiceStage implements TaskStage {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableServiceStage.class);

    private final String name;
    private final List<StepConfig> stepConfigs;

    /**
     * Step 配置
     */
    public static class StepConfig {
        private String stepName;
        private DataPreparer dataPreparer;
        private StageStep step;
        private ResultValidator resultValidator;

        private StepConfig() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final StepConfig config = new StepConfig();

            public Builder stepName(String stepName) {
                config.stepName = stepName;
                return this;
            }

            public Builder dataPreparer(DataPreparer dataPreparer) {
                config.dataPreparer = dataPreparer;
                return this;
            }

            public Builder step(StageStep step) {
                config.step = step;
                return this;
            }

            public Builder resultValidator(ResultValidator resultValidator) {
                config.resultValidator = resultValidator;
                return this;
            }

            public StepConfig build() {
                if (config.step == null) {
                    throw new IllegalArgumentException("step is required");
                }
                if (config.stepName == null) {
                    config.stepName = config.step.getStepName();
                }
                return config;
            }
        }

        // Getters
        public String getStepName() {
            return stepName;
        }

        public DataPreparer getDataPreparer() {
            return dataPreparer;
        }

        public StageStep getStep() {
            return step;
        }

        public ResultValidator getResultValidator() {
            return resultValidator;
        }
    }

    public ConfigurableServiceStage(String name, List<StepConfig> stepConfigs) {
        this.name = name;
        this.stepConfigs = stepConfigs != null ? stepConfigs : new ArrayList<>();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public StageResult execute(TaskRuntimeContext runtimeContext) {
        StageResult result = StageResult.start(name);

        log.info("开始执行 Stage: {}, 包含 {} 个 Step", name, stepConfigs.size());

        // 顺序执行每个 Step
        for (int i = 0; i < stepConfigs.size(); i++) {
            StepConfig stepConfig = stepConfigs.get(i);
            String stepName = stepConfig.getStepName();

            log.debug("执行 Step {}/{}: {}", i + 1, stepConfigs.size(), stepName);

            try {
                // 1. 准备数据
                if (stepConfig.getDataPreparer() != null) {
                    log.debug("Step '{}': 准备数据", stepName);
                    stepConfig.getDataPreparer().prepare(runtimeContext);
                }

                // 2. 执行 Step
                log.debug("Step '{}': 执行动作", stepName);
                stepConfig.getStep().execute(runtimeContext);  // ← 使用原有接口

                log.debug("Step '{}': 执行成功", stepName);

                // 3. 验证结果
                if (stepConfig.getResultValidator() != null) {
                    log.debug("Step '{}': 验证结果", stepName);
                    ValidationResult validationResult = stepConfig.getResultValidator().validate(runtimeContext);

                    if (!validationResult.isSuccess()) {
                        log.error("Step '{}' 结果验证失败: {}", stepName, validationResult.getMessage());
                        result.failure(FailureInfo.of(ErrorType.BUSINESS_ERROR, validationResult.getMessage()));
                        return result;
                    }

                    log.debug("Step '{}': 验证通过 - {}", stepName, validationResult.getMessage());
                }

            } catch (Exception e) {
                log.error("Step '{}' 执行异常", stepName, e);
                result.failure(FailureInfo.of(ErrorType.SYSTEM_ERROR,
                    String.format("Step '%s' 执行异常: %s", stepName, e.getMessage())));
                return result;
            }
        }

        result.success();
        log.info("Stage '{}' 执行成功，完成 {} 个 Step", name, stepConfigs.size());
        return result;
    }

    @Override
    public void rollback(TaskRuntimeContext ctx) {
        log.info("Stage '{}' 回滚占位（待实现）", name);
        // 可选的回滚逻辑
    }

    @Override
    public List<StageStep> getSteps() {
        return stepConfigs.stream()
            .map(StepConfig::getStep)
            .collect(Collectors.toList());
    }

    @Override
    public boolean canSkip(TaskRuntimeContext ctx) {
        return false;
    }
}

