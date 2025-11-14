package xyz.firestige.executor.unit.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.util.TimingExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ErrorType 枚举测试
 * 测试错误类型枚举
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("ErrorType 枚举测试")
class ErrorTypeTest {

    @Test
    @DisplayName("场景: 所有错误类型")
    void testAllErrorTypes() {
        // When: 获取所有错误类型
        ErrorType[] types = ErrorType.values();

        // Then: 包含所有预期类型
        assertTrue(types.length >= 5, "至少应该有5种错误类型");
        assertTrue(java.util.Arrays.asList(types).contains(ErrorType.VALIDATION_ERROR));
        assertTrue(java.util.Arrays.asList(types).contains(ErrorType.NETWORK_ERROR));
        assertTrue(java.util.Arrays.asList(types).contains(ErrorType.TIMEOUT_ERROR));
        assertTrue(java.util.Arrays.asList(types).contains(ErrorType.BUSINESS_ERROR));
        assertTrue(java.util.Arrays.asList(types).contains(ErrorType.SYSTEM_ERROR));
    }

    @Test
    @DisplayName("场景: 错误类型描述")
    void testErrorTypeDescriptions() {
        // Then: 每种类型都有描述
        assertNotNull(ErrorType.VALIDATION_ERROR.getDescription());
        assertNotNull(ErrorType.NETWORK_ERROR.getDescription());
        assertNotNull(ErrorType.TIMEOUT_ERROR.getDescription());
        assertNotNull(ErrorType.BUSINESS_ERROR.getDescription());
        assertNotNull(ErrorType.SYSTEM_ERROR.getDescription());

        // 验证描述不为空
        assertFalse(ErrorType.VALIDATION_ERROR.getDescription().isEmpty());
        assertFalse(ErrorType.NETWORK_ERROR.getDescription().isEmpty());
    }

    @Test
    @DisplayName("场景: 错误类型名称")
    void testErrorTypeNames() {
        // Then: 名称正确
        assertEquals("VALIDATION_ERROR", ErrorType.VALIDATION_ERROR.name());
        assertEquals("NETWORK_ERROR", ErrorType.NETWORK_ERROR.name());
        assertEquals("TIMEOUT_ERROR", ErrorType.TIMEOUT_ERROR.name());
        assertEquals("BUSINESS_ERROR", ErrorType.BUSINESS_ERROR.name());
        assertEquals("SYSTEM_ERROR", ErrorType.SYSTEM_ERROR.name());
    }
}

