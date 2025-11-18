package xyz.firestige.deploy.unit.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.util.TestDataFactory;
import xyz.firestige.deploy.util.TimingExtension;
import xyz.firestige.deploy.validation.ValidationResult;
import xyz.firestige.deploy.validation.validator.TenantIdValidator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TenantIdValidator 单元测试
 * 测试租户 ID 校验逻辑
 *
 * 预计执行时间：20 秒（4 个测试）
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("TenantIdValidator 单元测试")
class TenantIdValidatorTest {

    private final TenantIdValidator validator = new TenantIdValidator();

    @Test
    @DisplayName("场景 2.1.1: 租户 ID 为 null - 校验失败")
    void testTenantIdNull() {
        // Given: tenantId 为 null
        TenantDeployConfig config = new TenantDeployConfig();
        config.setTenantId(null);

        // When: 执行校验
        ValidationResult result = validator.validate(config);

        // Then: 校验失败
        assertFalse(result.isValid(), "tenantId 为 null 应该校验失败");
        assertTrue(result.hasErrors(), "应该有错误信息");
        assertTrue(result.getErrors().get(0).getMessage().contains("不能为空"),
                "错误消息应该说明不能为空");
    }

    @Test
    @DisplayName("场景 2.1.2: 租户 ID 过长 - 校验失败")
    void testTenantIdTooLong() {
        // Given: tenantId 长度 65 个字符
        TenantDeployConfig config = new TenantDeployConfig();
        config.setTenantId("a".repeat(65));

        // When: 执行校验
        ValidationResult result = validator.validate(config);

        // Then: 校验失败
        assertFalse(result.isValid(), "tenantId 过长应该校验失败");
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).getMessage().contains("64"),
                "错误消息应该说明最大长度");
    }

    @Test
    @DisplayName("场景 2.1.3: 租户 ID 包含非法字符 - 校验失败")
    void testTenantIdInvalidCharacters() {
        // Given: tenantId 包含非法字符
        TenantDeployConfig config = new TenantDeployConfig();
        config.setTenantId("tenant@123");  // @ 是非法字符

        // When: 执行校验
        ValidationResult result = validator.validate(config);

        // Then: 校验失败
        assertFalse(result.isValid(), "tenantId 包含非法字符应该校验失败");
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).getMessage().contains("字母、数字"),
                "错误消息应该说明允许的字符");
    }

    @Test
    @DisplayName("场景 2.1.4: 租户 ID 合法 - 校验通过")
    void testTenantIdValid() {
        // Given: 合法的 tenantId
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant_123-abc", 1001L);

        // When: 执行校验
        ValidationResult result = validator.validate(config);

        // Then: 校验通过
        assertTrue(result.isValid(), "合法 tenantId 应该校验通过");
        assertFalse(result.hasErrors(), "不应该有错误");
    }
}

