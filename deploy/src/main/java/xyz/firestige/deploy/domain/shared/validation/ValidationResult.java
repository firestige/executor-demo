package xyz.firestige.deploy.domain.shared.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验结果
 */
public class ValidationResult {

    /**
     * 是否通过校验
     */
    private boolean valid;

    /**
     * 校验错误列表
     */
    private List<ValidationError> errors;

    /**
     * 校验警告列表
     */
    private List<ValidationWarning> warnings;

    public ValidationResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.valid = true;
    }

    public ValidationResult(boolean valid) {
        this.valid = valid;
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    /**
     * 创建成功的校验结果
     */
    public static ValidationResult success() {
        return new ValidationResult(true);
    }

    /**
     * 创建失败的校验结果
     */
    public static ValidationResult failure(ValidationError error) {
        ValidationResult result = new ValidationResult(false);
        result.addError(error);
        return result;
    }

    /**
     * 创建失败的校验结果
     */
    public static ValidationResult failure(List<ValidationError> errors) {
        ValidationResult result = new ValidationResult(false);
        result.setErrors(errors);
        return result;
    }

    /**
     * 添加错误
     */
    public void addError(ValidationError error) {
        this.errors.add(error);
        this.valid = false;
    }

    /**
     * 添加警告
     */
    public void addWarning(ValidationWarning warning) {
        this.warnings.add(warning);
    }

    /**
     * 合并另一个校验结果
     */
    public void merge(ValidationResult other) {
        if (other != null) {
            this.errors.addAll(other.getErrors());
            this.warnings.addAll(other.getWarnings());
            if (!other.isValid()) {
                this.valid = false;
            }
        }
    }

    /**
     * 是否有错误
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * 是否有警告
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    // Getters and Setters

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public void setErrors(List<ValidationError> errors) {
        this.errors = errors;
        if (!errors.isEmpty()) {
            this.valid = false;
        }
    }

    public List<ValidationWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<ValidationWarning> warnings) {
        this.warnings = warnings;
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "valid=" + valid +
                ", errorCount=" + errors.size() +
                ", warningCount=" + warnings.size() +
                '}';
    }
}

