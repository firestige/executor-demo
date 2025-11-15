package xyz.firestige.executor.unit.validation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.util.TimingExtension;
import xyz.firestige.executor.validation.ValidationResult;
import xyz.firestige.executor.validation.validator.ConflictValidator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConflictValidator 单元测试
 * 测试租户冲突检测逻辑
 *
 * 预计执行时间：15 秒（3 个测试）
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("ConflictValidator 单元测试")
class ConflictValidatorTest {

    private final ConflictValidator validator = new ConflictValidator();

    @BeforeEach
    void setUp() {
        // 每个测试前清理运行中的租户
        ConflictValidator.clearAll();
    }

    @AfterEach
    void tearDown() {
        // 每个测试后清理运行中的租户
        ConflictValidator.clearAll();
    }

    @Test
    @DisplayName("场景 2.1.9: 检测租户冲突")
    void testTenantConflict() {
        // Given: 租户 tenant1 已在运行
        ConflictValidator.registerRunningTenant("tenant1", "task001");

        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);

        // When: 校验包含 tenant1 的配置
        ValidationResult result = validator.validate(config);

        // Then: 校验失败，提示冲突
        assertFalse(result.isValid(), "已运行的租户应该校验失败");
        assertTrue(result.hasErrors(), "应该有错误信息");
        String errorMsg = result.getErrors().get(0).getMessage();
        assertTrue(errorMsg.contains("执行") || errorMsg.contains("任务"),
                "错误消息应该说明租户在执行中");
        assertTrue(errorMsg.contains("tenant1"),
                "错误消息应该包含租户ID");
    }

    @Test
    @DisplayName("场景 2.1.10: 同批次内重复租户")
    void testDuplicateTenantInBatch() {
        // Given: 第一次校验 tenant1 通过
        TenantDeployConfig config1 = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        ValidationResult result1 = validator.validate(config1);
        assertTrue(result1.isValid(), "第一次校验应该通过");

        // 模拟将 tenant1 注册为运行中
        ConflictValidator.registerRunningTenant("tenant1", "task001");

        // When: 同批次再次校验 tenant1
        TenantDeployConfig config2 = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        ValidationResult result2 = validator.validate(config2);

        // Then: 第二次校验失败
        assertFalse(result2.isValid(), "重复的租户应该校验失败");
        assertTrue(result2.hasErrors());
    }

    @Test
    @DisplayName("场景 2.1.11: 无冲突校验通过")
    void testNoConflict() {
        // Given: 租户未在运行
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);

        // When: 执行校验
        ValidationResult result = validator.validate(config);

        // Then: 校验通过
        assertTrue(result.isValid(), "未运行的租户应该校验通过");
        assertFalse(result.hasErrors(), "不应该有错误");
    }
}

