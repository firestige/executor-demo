package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.executor.execution.checkpoint.Checkpoint;
import xyz.firestige.executor.execution.pipeline.InMemoryCheckpointManager;
import xyz.firestige.executor.util.TimingExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CheckpointManager 单元测试
 * 测试检查点管理功能
 *
 * 预计执行时间：30 秒（4 个测试）
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("CheckpointManager 单元测试")
class CheckpointManagerTest {

    private InMemoryCheckpointManager manager;

    @BeforeEach
    void setUp() {
        manager = new InMemoryCheckpointManager();
    }

    @Test
    @DisplayName("场景 4.3.1: 保存和加载检查点")
    void testSaveAndLoadCheckpoint() {
        // Given: 创建检查点
        Checkpoint checkpoint = new Checkpoint("task1", "stage2");

        // 保存 Stage 数据
        Map<String, Object> stageData = new HashMap<>();
        stageData.put("key1", "value1");
        stageData.put("completedStages", 2);
        checkpoint.saveStageData("stage2", stageData);

        // When: 保存检查点
        manager.saveCheckpoint("task1", "stage2", checkpoint);

        // Then: 可以加载检查点
        Checkpoint loaded = manager.loadCheckpoint("task1");
        assertNotNull(loaded, "应该能加载检查点");
        assertEquals("task1", loaded.getTaskId());
        assertEquals("stage2", loaded.getStageName());

        // 验证 Stage 数据
        assertTrue(loaded.hasStageData("stage2"), "应该有 stage2 的数据");
        Map<String, Object> loadedData = loaded.getStageData("stage2");
        assertEquals("value1", loadedData.get("key1"));
        assertEquals(2, loadedData.get("completedStages"));
    }

    @Test
    @DisplayName("场景 4.3.2: 加载不存在的检查点返回null")
    void testLoadNonexistentCheckpoint() {
        // When: 加载不存在的检查点
        Checkpoint checkpoint = manager.loadCheckpoint("nonexistent");

        // Then: 返回null
        assertNull(checkpoint, "不存在的检查点应该返回null");
    }

    @Test
    @DisplayName("场景 4.3.3: 清除检查点")
    void testClearCheckpoint() {
        // Given: 保存检查点
        Checkpoint checkpoint = new Checkpoint("task1", "stage2");
        manager.saveCheckpoint("task1", "stage2", checkpoint);

        // 验证检查点存在
        assertTrue(manager.hasCheckpoint("task1"), "检查点应该存在");

        // When: 清除检查点
        manager.clearCheckpoint("task1");

        // Then: 检查点不存在
        assertFalse(manager.hasCheckpoint("task1"), "检查点应该被清除");
        assertNull(manager.loadCheckpoint("task1"), "加载应该返回null");
    }

    @Test
    @DisplayName("场景 4.3.4: 检查检查点是否存在")
    void testHasCheckpoint() {
        // Given: 初始没有检查点
        assertFalse(manager.hasCheckpoint("task1"), "初始不应该有检查点");

        // When: 保存检查点
        Checkpoint checkpoint = new Checkpoint("task1", "stage1");
        manager.saveCheckpoint("task1", "stage1", checkpoint);

        // Then: 检查点存在
        assertTrue(manager.hasCheckpoint("task1"), "保存后应该有检查点");

        // 其他任务没有检查点
        assertFalse(manager.hasCheckpoint("task2"), "其他任务不应该有检查点");
    }
}

