package xyz.firestige.executor.unit.orchestration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.executor.orchestration.ExecutionUnitStatus;
import xyz.firestige.executor.util.TimingExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionUnitStatus 枚举测试
 * 测试执行单状态枚举
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("ExecutionUnitStatus 单元测试")
class ExecutionUnitStatusTest {

    @Test
    @DisplayName("场景: 所有状态值")
    void testAllStatuses() {
        // When: 获取所有状态
        ExecutionUnitStatus[] statuses = ExecutionUnitStatus.values();

        // Then: 包含所有预期状态
        assertTrue(statuses.length >= 4, "至少应该有4个状态");
        assertTrue(java.util.Arrays.asList(statuses).contains(ExecutionUnitStatus.CREATED));
        assertTrue(java.util.Arrays.asList(statuses).contains(ExecutionUnitStatus.SCHEDULED));
        assertTrue(java.util.Arrays.asList(statuses).contains(ExecutionUnitStatus.RUNNING));
        assertTrue(java.util.Arrays.asList(statuses).contains(ExecutionUnitStatus.COMPLETED));
    }

    @Test
    @DisplayName("场景: 状态名称")
    void testStatusNames() {
        // Then: 状态名称正确
        assertEquals("CREATED", ExecutionUnitStatus.CREATED.name());
        assertEquals("SCHEDULED", ExecutionUnitStatus.SCHEDULED.name());
        assertEquals("RUNNING", ExecutionUnitStatus.RUNNING.name());
        assertEquals("COMPLETED", ExecutionUnitStatus.COMPLETED.name());
    }
}

