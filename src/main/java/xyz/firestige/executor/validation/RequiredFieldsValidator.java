package xyz.firestige.executor.validation;

import xyz.firestige.dto.deploy.TenantDeployConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单必填字段验证器：用于测试验证失败场景。
 * 检查 deployUnitId / deployUnitVersion / deployUnitName 是否非空。
 */
public class RequiredFieldsValidator implements ConfigValidator {

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public String getValidatorName() {
        return "required-fields";
    }

    @Override
    public ValidationResult validate(TenantDeployConfig config) {
        List<ValidationError> errors = new ArrayList<>();
        if (config.getDeployUnitId() == null) {
            errors.add(ValidationError.of("deployUnitId", "deployUnitId is null"));
        }
        if (config.getDeployUnitVersion() == null) {
            errors.add(ValidationError.of("deployUnitVersion", "deployUnitVersion is null"));
        }
        if (config.getDeployUnitName() == null || config.getDeployUnitName().isEmpty()) {
            errors.add(ValidationError.of("deployUnitName", "deployUnitName is blank"));
        }
        if (errors.isEmpty()) {
            return ValidationResult.success();
        }
        return ValidationResult.failure(errors);
    }
}
