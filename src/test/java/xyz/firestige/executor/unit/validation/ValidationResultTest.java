package xyz.firestige.executor.unit.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.executor.util.TimingExtension;
import xyz.firestige.executor.validation.ValidationError;
import xyz.firestige.executor.validation.ValidationResult;
import xyz.firestige.executor.validation.ValidationWarning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ValidationResult 单元测试
 * 测试校验结果封装类
 *
 * 预计执行时间：20 秒（3 个测试）
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("ValidationResult 单元测试")
class ValidationResultTest {

    @Test
    @DisplayName("场景 1.3.1: 创建成功结果")
    void testSuccessResult() {
        // When: 创建成功结果
        ValidationResult result = ValidationResult.success();

        // Then: 结果有效，无错误
        assertTrue(result.isValid(), "成功结果应该是有效的");
        assertFalse(result.hasErrors(), "成功结果不应该有错误");
        assertTrue(result.getErrors().isEmpty(), "错误列表应该为空");
    }

    @Test
    @DisplayName("场景 1.3.2: 添加错误后变为失败")
    void testAddError() {
        // Given: 一个有效的结果
        ValidationResult result = new ValidationResult(true);
        assertTrue(result.isValid(), "初始应该是有效的");

        // When: 添加错误
        ValidationError error = ValidationError.of("field1", "错误消息");
        result.addError(error);

        // Then: 结果变为无效
        assertFalse(result.isValid(), "添加错误后应该无效");
        assertTrue(result.hasErrors(), "应该有错误");
        assertEquals(1, result.getErrors().size(), "应该有 1 个错误");
        assertEquals("错误消息", result.getErrors().get(0).getMessage());
    }

    @Test
    @DisplayName("场景 1.3.3: 合并多个校验结果")
    void testMergeResults() {
        // Given: 两个校验结果
        ValidationResult result1 = ValidationResult.success();
        result1.addWarning(ValidationWarning.of("warning1", "警告1"));

        ValidationResult result2 = new ValidationResult(false);
        result2.addError(ValidationError.of("field2", "错误2"));
        result2.addWarning(ValidationWarning.of("warning2", "警告2"));

        // When: 合并结果（merge 是 void 方法，直接修改 result1）
        result1.merge(result2);

        // Then: 合并后 result1 包含所有错误和警告
        assertFalse(result1.isValid(), "合并后应该无效（因为 result2 有错误）");
        assertEquals(1, result1.getErrors().size(), "应该有 1 个错误");
        assertEquals(2, result1.getWarnings().size(), "应该有 2 个警告");
    }
}

