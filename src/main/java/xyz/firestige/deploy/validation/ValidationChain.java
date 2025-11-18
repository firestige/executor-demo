package xyz.firestige.deploy.validation;

import xyz.firestige.dto.deploy.TenantDeployConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 校验链
 * 按顺序执行多个校验器
 */
public class ValidationChain {

    /**
     * 校验器列表
     */
    private List<ConfigValidator> validators;

    /**
     * 是否快速失败（遇到第一个错误就停止）
     */
    private boolean failFast;

    public ValidationChain() {
        this.validators = new ArrayList<>();
        this.failFast = false;
    }

    public ValidationChain(boolean failFast) {
        this.validators = new ArrayList<>();
        this.failFast = failFast;
    }

    /**
     * 添加校验器
     */
    public ValidationChain addValidator(ConfigValidator validator) {
        this.validators.add(validator);
        // 按 order 排序
        this.validators.sort(Comparator.comparingInt(ConfigValidator::getOrder));
        return this;
    }

    /**
     * 添加多个校验器
     */
    public ValidationChain addValidators(List<ConfigValidator> validators) {
        this.validators.addAll(validators);
        // 按 order 排序
        this.validators.sort(Comparator.comparingInt(ConfigValidator::getOrder));
        return this;
    }

    /**
     * 校验单个配置
     */
    public ValidationResult validate(TenantDeployConfig config) {
        ValidationResult result = new ValidationResult(true);

        for (ConfigValidator validator : validators) {
            ValidationResult validatorResult = validator.validate(config);
            result.merge(validatorResult);

            // 如果快速失败模式且当前有错误，则停止
            if (failFast && !validatorResult.isValid()) {
                break;
            }
        }

        return result;
    }

    /**
     * 校验所有配置
     */
    public ValidationSummary validateAll(List<TenantDeployConfig> configs) {
        ValidationSummary summary = new ValidationSummary();
        summary.setTotalConfigs(configs.size());

        for (TenantDeployConfig config : configs) {
            ValidationResult result = validate(config);

            if (result.isValid()) {
                summary.addValidConfig(config);
            } else {
                summary.addInvalidConfig(config, result.getErrors());
            }

            // 收集所有警告
            if (result.hasWarnings()) {
                summary.addWarnings(result.getWarnings());
            }
        }

        return summary;
    }

    /**
     * 获取所有校验器名称
     */
    public List<String> getValidatorNames() {
        return validators.stream()
                .map(ConfigValidator::getValidatorName)
                .collect(Collectors.toList());
    }

    // Getters and Setters

    public List<ConfigValidator> getValidators() {
        return validators;
    }

    public void setValidators(List<ConfigValidator> validators) {
        this.validators = validators;
        this.validators.sort(Comparator.comparingInt(ConfigValidator::getOrder));
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }
}

