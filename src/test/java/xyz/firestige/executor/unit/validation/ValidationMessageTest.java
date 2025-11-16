package xyz.firestige.executor.unit.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.executor.util.TimingExtension;
import xyz.firestige.executor.validation.ValidationError;
import xyz.firestige.executor.validation.ValidationWarning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ValidationError 和 ValidationWarning 单元测试
 * 测试校验消息对象
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("ValidationError/Warning 单元测试")
class ValidationMessageTest {

    @Test
    @DisplayName("场景: 创建校验错误")
    void testCreateValidationError() {
        // When: 创建错误
        ValidationError error = ValidationError.of("field1", "字段不能为空");

        // Then: 属性正确
        assertNotNull(error);
        assertEquals("field1", error.getField());
        assertEquals("字段不能为空", error.getMessage());
    }

    @Test
    @DisplayName("场景: 创建带值的校验错误")
    void testCreateValidationErrorWithValue() {
        // When: 创建带值的错误
        ValidationError error = ValidationError.of("age", "年龄超出范围", "150");

        // Then: 包含值信息
        assertEquals("age", error.getField());
        assertEquals("年龄超出范围", error.getMessage());
        assertEquals("150", error.getRejectedValue());
    }

    @Test
    @DisplayName("场景: 创建校验警告")
    void testCreateValidationWarning() {
        // When: 创建警告
        ValidationWarning warning = ValidationWarning.of("field2", "建议填写完整信息");

        // Then: 属性正确
        assertNotNull(warning);
        assertEquals("field2", warning.getField());
        assertEquals("建议填写完整信息", warning.getMessage());
    }

    @Test
    @DisplayName("场景: 错误和警告的区别")
    void testErrorVsWarning() {
        // Given: 错误和警告
        ValidationError error = ValidationError.of("field", "错误");
        ValidationWarning warning = ValidationWarning.of("field", "警告");

        // Then: 是不同的类型
        assertNotEquals(error.getClass(), warning.getClass());
        assertInstanceOf(ValidationError.class, error);
        assertInstanceOf(ValidationWarning.class, warning);
    }
}

