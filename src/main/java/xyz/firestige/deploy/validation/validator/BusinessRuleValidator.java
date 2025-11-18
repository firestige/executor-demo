package xyz.firestige.deploy.validation.validator;

import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.validation.ConfigValidator;
import xyz.firestige.deploy.validation.ValidationResult;
import xyz.firestige.deploy.validation.ValidationWarning;

/**
 * 业务规则校验器（示例）
 * 用于演示如何实现自定义业务规则校验
 */
public class BusinessRuleValidator implements ConfigValidator {

    @Override
    public ValidationResult validate(TenantDeployConfig config) {
        ValidationResult result = new ValidationResult(true);

        // 示例规则 1: 检查网络端点数量
        if (config.getNetworkEndpoints() != null) {
            int endpointCount = config.getNetworkEndpoints().size();

            // 如果端点数量过多，给出警告
            if (endpointCount > 10) {
                result.addWarning(ValidationWarning.of(
                        "networkEndpoints",
                        "网络端点数量(" + endpointCount + ")较多，可能影响性能"
                ));
            }

            // 如果只有一个端点，给出警告（可能缺少冗余）
            if (endpointCount == 1) {
                result.addWarning(ValidationWarning.of(
                        "networkEndpoints",
                        "只配置了一个网络端点，建议配置多个端点以提供冗余"
                ));
            }
        }

        // 示例规则 2: 检查租户 ID 命名规范（警告级别）
        String tenantId = config.getTenantId();
        if (tenantId != null && tenantId.startsWith("test_")) {
            result.addWarning(ValidationWarning.of(
                    "tenantId",
                    "租户 ID 以 'test_' 开头，请确认这不是测试数据"
            ));
        }

        // 可以添加更多业务规则...

        return result;
    }

    @Override
    public String getValidatorName() {
        return "BusinessRuleValidator";
    }

    @Override
    public int getOrder() {
        return 50; // 业务规则校验在基础校验之后
    }
}

