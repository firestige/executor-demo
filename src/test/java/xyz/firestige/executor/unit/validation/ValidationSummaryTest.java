package xyz.firestige.executor.unit.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.util.TimingExtension;
import xyz.firestige.executor.validation.ValidationError;
import xyz.firestige.executor.validation.ValidationSummary;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ValidationSummary 单元测试
 * 测试校验摘要功能
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("ValidationSummary 单元测试")
class ValidationSummaryTest {

    @Test
    @DisplayName("场景: 空摘要")
    void testEmptySummary() {
        // When: 创建空摘要
        ValidationSummary summary = new ValidationSummary();

        // Then: 统计正确
        assertEquals(0, summary.getValidCount());
        assertEquals(0, summary.getInvalidCount());
        assertFalse(summary.hasErrors());
    }

    @Test
    @DisplayName("场景: 添加有效配置")
    void testAddValidConfig() {
        // Given: 摘要
        ValidationSummary summary = new ValidationSummary();
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);

        // When: 添加有效配置
        summary.addValidConfig(config);
        summary.addValidConfig(TestDataFactory.createMinimalConfig("tenant2", 1001L));
        summary.setTotalConfigs(2);

        // Then: 统计正确
        assertEquals(2, summary.getValidCount());
        assertEquals(0, summary.getInvalidCount());
        assertEquals(2, summary.getTotalConfigs());
        assertFalse(summary.hasErrors());
    }

    @Test
    @DisplayName("场景: 添加无效配置")
    void testAddInvalidConfig() {
        // Given: 摘要和错误
        ValidationSummary summary = new ValidationSummary();
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        List<ValidationError> errors = List.of(
            ValidationError.of("field1", "错误1"),
            ValidationError.of("field2", "错误2")
        );

        // When: 添加无效配置
        summary.addInvalidConfig(config, errors);
        summary.setTotalConfigs(1);

        // Then: 统计正确
        assertEquals(0, summary.getValidCount());
        assertEquals(1, summary.getInvalidCount());
        assertTrue(summary.hasErrors());
        assertEquals(2, summary.getAllErrors().size());
    }

    @Test
    @DisplayName("场景: 混合有效和无效配置")
    void testMixedConfigs() {
        // Given: 摘要
        ValidationSummary summary = new ValidationSummary();

        // When: 添加混合配置
        summary.addValidConfig(TestDataFactory.createMinimalConfig("tenant1", 1001L));
        summary.addValidConfig(TestDataFactory.createMinimalConfig("tenant2", 1001L));

        TenantDeployConfig invalid = TestDataFactory.createMinimalConfig("tenant3", 1001L);
        summary.addInvalidConfig(invalid, List.of(ValidationError.of("field", "错误")));

        summary.setTotalConfigs(3);

        // Then: 统计正确
        assertEquals(3, summary.getTotalConfigs());
        assertEquals(2, summary.getValidCount());
        assertEquals(1, summary.getInvalidCount());
        assertTrue(summary.hasErrors());
    }

    @Test
    @DisplayName("场景: 获取所有错误")
    void testGetAllErrors() {
        // Given: 摘要和多个无效配置
        ValidationSummary summary = new ValidationSummary();

        TenantDeployConfig config1 = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        summary.addInvalidConfig(config1, List.of(
            ValidationError.of("field1", "错误1"),
            ValidationError.of("field2", "错误2")
        ));

        TenantDeployConfig config2 = TestDataFactory.createMinimalConfig("tenant2", 1001L);
        summary.addInvalidConfig(config2, List.of(
            ValidationError.of("field3", "错误3")
        ));

        // When: 获取所有错误
        List<ValidationError> allErrors = summary.getAllErrors();

        // Then: 包含所有错误
        assertEquals(3, allErrors.size());
    }
}

