package xyz.firestige.deploy.config.stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配置验证结果
 *
 * <p>不可变对象，包含验证状态、警告和错误信息。
 *
 * @since T-017
 */
public class ValidationResult {

    private final boolean valid;
    private final List<String> warnings;
    private final List<String> errors;

    private ValidationResult(boolean valid, List<String> warnings, List<String> errors) {
        this.valid = valid;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    /**
     * 创建成功结果
     */
    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), List.of());
    }

    /**
     * 创建带警告的成功结果
     */
    public static ValidationResult warning(String message) {
        return new ValidationResult(true, List.of(message), List.of());
    }

    /**
     * 创建失败结果
     */
    public static ValidationResult error(String message) {
        return new ValidationResult(false, List.of(), List.of(message));
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public boolean isValid() {
        return valid;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getErrors() {
        return errors;
    }

    /**
     * ValidationResult Builder
     */
    public static class Builder {
        private boolean valid = true;
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        public Builder warning(String message) {
            this.warnings.add(message);
            return this;
        }

        public Builder error(String message) {
            this.errors.add(message);
            this.valid = false;
            return this;
        }

        public Builder warnings(List<String> messages) {
            this.warnings.addAll(messages);
            return this;
        }

        public Builder errors(List<String> messages) {
            this.errors.addAll(messages);
            if (!messages.isEmpty()) {
                this.valid = false;
            }
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(valid, warnings, errors);
        }
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "valid=" + valid +
                ", warnings=" + warnings.size() +
                ", errors=" + errors.size() +
                '}';
    }
}

