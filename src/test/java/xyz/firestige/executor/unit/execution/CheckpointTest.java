package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.TenantDeployConfig;
import xyz.firestige.executor.execution.checkpoint.Checkpoint;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.util.TimingExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checkpoint 单元测试
 * 测试检查点数据类
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("Checkpoint 单元测试")
class CheckpointTest {

    private Checkpoint checkpoint;

    @BeforeEach
    void setUp() {
        checkpoint = new Checkpoint("task1", "stage2");
    }

    @Test
    @DisplayName("场景: 基本属性")
    void testBasicProperties() {
        // Then: 基本属性正确
        assertEquals("task1", checkpoint.getTaskId());
        assertEquals("stage2", checkpoint.getStageName());
        assertNotNull(checkpoint.getTimestamp());
    }

    @Test
    @DisplayName("场景: 保存和获取Stage数据")
    void testSaveAndGetStageData() {
        // Given: Stage数据
        Map<String, Object> stageData = new HashMap<>();
        stageData.put("completedCount", 5);
        stageData.put("status", "running");

        // When: 保存数据
        checkpoint.saveStageData("stage1", stageData);

        // Then: 可以获取数据
        assertTrue(checkpoint.hasStageData("stage1"));
        Map<String, Object> retrieved = checkpoint.getStageData("stage1");
        assertEquals(5, retrieved.get("completedCount"));
        assertEquals("running", retrieved.get("status"));
    }

    @Test
    @DisplayName("场景: 多个Stage数据")
    void testMultipleStageData() {
        // Given: 多个Stage的数据
        Map<String, Object> stage1Data = new HashMap<>();
        stage1Data.put("key1", "value1");

        Map<String, Object> stage2Data = new HashMap<>();
        stage2Data.put("key2", "value2");

        // When: 保存多个Stage数据
        checkpoint.saveStageData("stage1", stage1Data);
        checkpoint.saveStageData("stage2", stage2Data);

        // Then: 都可以获取
        assertTrue(checkpoint.hasStageData("stage1"));
        assertTrue(checkpoint.hasStageData("stage2"));
        assertEquals("value1", checkpoint.getStageData("stage1").get("key1"));
        assertEquals("value2", checkpoint.getStageData("stage2").get("key2"));
    }

    @Test
    @DisplayName("场景: 不存在的Stage数据")
    void testNonexistentStageData() {
        // When: 查询不存在的Stage
        boolean hasData = checkpoint.hasStageData("nonexistent");

        // Then: 返回false
        assertFalse(hasData);
        assertNull(checkpoint.getStageData("nonexistent"));
    }

    @Test
    @DisplayName("场景: 覆盖Stage数据")
    void testOverwriteStageData() {
        // Given: 初始数据
        Map<String, Object> originalData = new HashMap<>();
        originalData.put("count", 1);
        checkpoint.saveStageData("stage1", originalData);

        // When: 覆盖数据
        Map<String, Object> newData = new HashMap<>();
        newData.put("count", 2);
        checkpoint.saveStageData("stage1", newData);

        // Then: 数据被覆盖
        assertEquals(2, checkpoint.getStageData("stage1").get("count"));
    }
}

