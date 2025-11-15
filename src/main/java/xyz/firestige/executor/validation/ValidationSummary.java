package xyz.firestige.executor.validation;

import xyz.firestige.dto.deploy.TenantDeployConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 校验摘要
 * 汇总多个配置的校验结果
 */
public class ValidationSummary {

    /**
     * 总配置数
     */
    private int totalConfigs;

    /**
     * 有效的配置列表
     */
    private List<TenantDeployConfig> validConfigs;

    /**
     * 无效的配置及其错误映射
     */
    private Map<TenantDeployConfig, List<ValidationError>> invalidConfigs;

    /**
     * 所有警告列表
     */
    private List<ValidationWarning> warnings;

    public ValidationSummary() {
        this.validConfigs = new ArrayList<>();
        this.invalidConfigs = new HashMap<>();
        this.warnings = new ArrayList<>();
    }

    /**
     * 添加有效配置
     */
    public void addValidConfig(TenantDeployConfig config) {
        this.validConfigs.add(config);
    }

    /**
     * 添加无效配置
     */
    public void addInvalidConfig(TenantDeployConfig config, List<ValidationError> errors) {
        this.invalidConfigs.put(config, errors);
    }

    /**
     * 添加警告
     */
    public void addWarning(ValidationWarning warning) {
        this.warnings.add(warning);
    }

    /**
     * 添加警告列表
     */
    public void addWarnings(List<ValidationWarning> warnings) {
        this.warnings.addAll(warnings);
    }

    /**
     * 是否有错误
     */
    public boolean hasErrors() {
        return !invalidConfigs.isEmpty();
    }

    /**
     * 是否有警告
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * 获取所有错误
     */
    public List<ValidationError> getAllErrors() {
        List<ValidationError> allErrors = new ArrayList<>();
        for (List<ValidationError> errors : invalidConfigs.values()) {
            allErrors.addAll(errors);
        }
        return allErrors;
    }

    /**
     * 获取有效配置数量
     */
    public int getValidCount() {
        return validConfigs.size();
    }

    /**
     * 获取无效配置数量
     */
    public int getInvalidCount() {
        return invalidConfigs.size();
    }

    // Getters and Setters

    public int getTotalConfigs() {
        return totalConfigs;
    }

    public void setTotalConfigs(int totalConfigs) {
        this.totalConfigs = totalConfigs;
    }

    public List<TenantDeployConfig> getValidConfigs() {
        return validConfigs;
    }

    public void setValidConfigs(List<TenantDeployConfig> validConfigs) {
        this.validConfigs = validConfigs;
    }

    public Map<TenantDeployConfig, List<ValidationError>> getInvalidConfigs() {
        return invalidConfigs;
    }

    public void setInvalidConfigs(Map<TenantDeployConfig, List<ValidationError>> invalidConfigs) {
        this.invalidConfigs = invalidConfigs;
    }

    public List<ValidationWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<ValidationWarning> warnings) {
        this.warnings = warnings;
    }

    @Override
    public String toString() {
        return "ValidationSummary{" +
                "totalConfigs=" + totalConfigs +
                ", validCount=" + getValidCount() +
                ", invalidCount=" + getInvalidCount() +
                ", warningCount=" + warnings.size() +
                '}';
    }
}

