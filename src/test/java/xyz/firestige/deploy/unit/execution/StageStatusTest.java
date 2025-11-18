package xyz.firestige.deploy.unit.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.deploy.execution.StageStatus;
import xyz.firestige.deploy.util.TimingExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StageStatus 枚举测试
 * 测试Stage状态枚举
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("StageStatus 单元测试")
class StageStatusTest {

    @Test
    @DisplayName("场景: 所有Stage状态")
    void testAllStageStatuses() {
        // When: 获取所有状态
        StageStatus[] statuses = StageStatus.values();

        // Then: 包含核心状态
        assertTrue(statuses.length >= 4, "至少应该有4个状态");
        assertTrue(java.util.Arrays.asList(statuses).contains(StageStatus.PENDING));
        assertTrue(java.util.Arrays.asList(statuses).contains(StageStatus.RUNNING));
        assertTrue(java.util.Arrays.asList(statuses).contains(StageStatus.COMPLETED));
        assertTrue(java.util.Arrays.asList(statuses).contains(StageStatus.FAILED));
    }

    @Test
    @DisplayName("场景: 状态名称")
    void testStatusNames() {
        // Then: 状态名称正确
        assertEquals("PENDING", StageStatus.PENDING.name());
        assertEquals("RUNNING", StageStatus.RUNNING.name());
        assertEquals("COMPLETED", StageStatus.COMPLETED.name());
        assertEquals("FAILED", StageStatus.FAILED.name());
    }

    @Test
    @DisplayName("场景: 成功和失败状态")
    void testSuccessAndFailureStates() {
        // Given: 成功和失败状态
        StageStatus completed = StageStatus.COMPLETED;
        StageStatus failed = StageStatus.FAILED;

        // Then: 是不同的状态
        assertNotEquals(completed, failed);
    }
}

