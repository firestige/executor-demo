package xyz.firestige.executor.validation.validator;

import xyz.firestige.dto.TenantDeployConfig;
import xyz.firestige.executor.validation.ConfigValidator;
import xyz.firestige.executor.validation.ValidationError;
import xyz.firestige.executor.validation.ValidationResult;

/**
 * 租户 ID 校验器
 * 校验租户 ID 的有效性
 */
public class TenantIdValidator implements ConfigValidator {

    @Override
    public ValidationResult validate(TenantDeployConfig config) {
        ValidationResult result = new ValidationResult(true);

        String tenantId = config.getTenantId();

        // 校验租户 ID 不为空
        if (tenantId == null || tenantId.trim().isEmpty()) {
            result.addError(ValidationError.of(
                    "tenantId",
                    "租户 ID 不能为空"
            ));
            return result;
        }

        // 校验租户 ID 长度
        if (tenantId.length() > 64) {
            result.addError(ValidationError.of(
                    "tenantId",
                    "租户 ID 长度不能超过 64 个字符",
                    tenantId
            ));
        }

        // 校验租户 ID 格式（只能包含字母、数字、下划线、中划线）
        if (!tenantId.matches("^[a-zA-Z0-9_-]+$")) {
            result.addError(ValidationError.of(
                    "tenantId",
                    "租户 ID 只能包含字母、数字、下划线和中划线",
                    tenantId
            ));
        }

        return result;
    }

    @Override
    public String getValidatorName() {
        return "TenantIdValidator";
    }

    @Override
    public int getOrder() {
        return 5; // 优先级高，先校验基础字段
    }
}

