package xyz.firestige.executor.unit.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.util.TimingExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TaskStatus 枚举测试
 * 测试任务状态枚举
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("TaskStatus 单元测试")
class TaskStatusTest {

    @Test
    @DisplayName("场景: 所有任务状态")
    void testAllTaskStatuses() {
        // When: 获取所有状态
        TaskStatus[] statuses = TaskStatus.values();

        // Then: 包含核心状态
        assertTrue(statuses.length >= 6, "至少应该有6个状态");
        assertTrue(Arrays.asList(statuses).contains(TaskStatus.CREATED));
        assertTrue(Arrays.asList(statuses).contains(TaskStatus.RUNNING));
        assertTrue(Arrays.asList(statuses).contains(TaskStatus.PAUSED));
        assertTrue(Arrays.asList(statuses).contains(TaskStatus.COMPLETED));
        assertTrue(Arrays.asList(statuses).contains(TaskStatus.FAILED));
    }

    @Test
    @DisplayName("场景: 状态名称")
    void testStatusNames() {
        // Then: 状态名称正确
        assertEquals("CREATED", TaskStatus.CREATED.name());
        assertEquals("RUNNING", TaskStatus.RUNNING.name());
        assertEquals("PAUSED", TaskStatus.PAUSED.name());
        assertEquals("COMPLETED", TaskStatus.COMPLETED.name());
        assertEquals("FAILED", TaskStatus.FAILED.name());
    }

    @Test
    @DisplayName("场景: 终态判断")
    void testFinalStates() {
        // Given: 终态
        TaskStatus completed = TaskStatus.COMPLETED;
        TaskStatus failed = TaskStatus.FAILED;

        // Given: 非终态
        TaskStatus running = TaskStatus.RUNNING;
        TaskStatus paused = TaskStatus.PAUSED;

        // Then: 终态和非终态可以区分
        assertNotEquals(completed, running);
        assertNotEquals(failed, paused);
    }
}

