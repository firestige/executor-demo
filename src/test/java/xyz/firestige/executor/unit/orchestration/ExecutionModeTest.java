package xyz.firestige.executor.unit.orchestration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.executor.orchestration.ExecutionMode;
import xyz.firestige.executor.util.TimingExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExecutionMode 枚举测试
 * 测试执行模式枚举
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("ExecutionMode 单元测试")
class ExecutionModeTest {

    @Test
    @DisplayName("场景: 并发模式")
    void testConcurrentMode() {
        // When: 获取并发模式
        ExecutionMode mode = ExecutionMode.CONCURRENT;

        // Then: 属性正确
        assertNotNull(mode);
        assertEquals("CONCURRENT", mode.name());
    }

    @Test
    @DisplayName("场景: FIFO模式")
    void testFifoMode() {
        // When: 获取FIFO模式
        ExecutionMode mode = ExecutionMode.FIFO;

        // Then: 属性正确
        assertNotNull(mode);
        assertEquals("FIFO", mode.name());
    }

    @Test
    @DisplayName("场景: 枚举值数量")
    void testEnumValues() {
        // When: 获取所有枚举值
        ExecutionMode[] modes = ExecutionMode.values();

        // Then: 应该有2个模式
        assertEquals(2, modes.length);
        assertTrue(Arrays.asList(modes).contains(ExecutionMode.CONCURRENT));
        assertTrue(Arrays.asList(modes).contains(ExecutionMode.FIFO));
    }
}

