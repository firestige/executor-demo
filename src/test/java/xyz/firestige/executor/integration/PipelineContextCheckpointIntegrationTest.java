package xyz.firestige.executor.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.TenantDeployConfig;
import xyz.firestige.executor.execution.checkpoint.Checkpoint;
import xyz.firestige.executor.execution.pipeline.InMemoryCheckpointManager;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.util.TimingExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PipelineContext 与 CheckpointManager 集成测试
 * 测试上下文和检查点的协作
 */
@Tag("integration")
@Tag("standard")
@ExtendWith(TimingExtension.class)
@DisplayName("PipelineContext + CheckpointManager 集成测试")
class PipelineContextCheckpointIntegrationTest {

    @Test
    @DisplayName("场景: 上下文数据可以保存到检查点")
    void testSaveContextToCheckpoint() {
        // Given: 上下文和检查点管理器
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        PipelineContext context = new PipelineContext("task1", config);
        InMemoryCheckpointManager checkpointManager = new InMemoryCheckpointManager();

        // When: 上下文中添加数据
        context.putData("processedCount", 10);
        context.putData("currentStage", "Stage2");

        // 创建检查点并保存上下文数据
        Checkpoint checkpoint = new Checkpoint("task1", "Stage2");
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("processedCount", context.getData("processedCount"));
        contextData.put("currentStage", context.getData("currentStage"));
        checkpoint.saveStageData("Stage2", contextData);

        checkpointManager.saveCheckpoint("task1", "Stage2", checkpoint);

        // Then: 可以从检查点恢复数据
        Checkpoint loaded = checkpointManager.loadCheckpoint("task1");
        assertNotNull(loaded);
        Map<String, Object> restoredData = loaded.getStageData("Stage2");
        assertEquals(10, restoredData.get("processedCount"));
        assertEquals("Stage2", restoredData.get("currentStage"));
    }

    @Test
    @DisplayName("场景: 暂停时保存检查点，恢复时加载")
    void testPauseAndResume() {
        // Given: 上下文和检查点管理器
        PipelineContext context = new PipelineContext("task1",
            TestDataFactory.createMinimalConfig("tenant1", 1001L));
        InMemoryCheckpointManager checkpointManager = new InMemoryCheckpointManager();

        // When: 执行到某个阶段后暂停
        context.putData("completedStages", 3);
        context.requestPause();

        // 保存检查点
        Checkpoint checkpoint = new Checkpoint("task1", "Stage3");
        Map<String, Object> pauseData = new HashMap<>();
        pauseData.put("completedStages", 3);
        pauseData.put("pauseRequested", true);
        checkpoint.saveStageData("Stage3", pauseData);
        checkpointManager.saveCheckpoint("task1", "Stage3", checkpoint);

        // Then: 可以检测到暂停状态
        assertTrue(context.shouldPause());
        assertTrue(checkpointManager.hasCheckpoint("task1"));

        // When: 恢复
        context.resume();

        // Then: 暂停标志清除，但检查点仍存在
        assertFalse(context.shouldPause());
        assertTrue(checkpointManager.hasCheckpoint("task1"));

        // 可以从检查点恢复数据
        Checkpoint loadedCheckpoint = checkpointManager.loadCheckpoint("task1");
        Map<String, Object> resumeData = loadedCheckpoint.getStageData("Stage3");
        assertEquals(3, resumeData.get("completedStages"));
    }

    @Test
    @DisplayName("场景: 多个阶段的检查点保存")
    void testMultipleStageCheckpoints() {
        // Given: 检查点管理器
        InMemoryCheckpointManager checkpointManager = new InMemoryCheckpointManager();

        // When: 保存多个阶段的检查点
        Checkpoint checkpoint = new Checkpoint("task1", "Stage3");

        // Stage1 数据
        Map<String, Object> stage1Data = new HashMap<>();
        stage1Data.put("status", "completed");
        checkpoint.saveStageData("Stage1", stage1Data);

        // Stage2 数据
        Map<String, Object> stage2Data = new HashMap<>();
        stage2Data.put("status", "completed");
        checkpoint.saveStageData("Stage2", stage2Data);

        // Stage3 数据
        Map<String, Object> stage3Data = new HashMap<>();
        stage3Data.put("status", "running");
        checkpoint.saveStageData("Stage3", stage3Data);

        checkpointManager.saveCheckpoint("task1", "Stage3", checkpoint);

        // Then: 可以加载所有阶段的数据
        Checkpoint loaded = checkpointManager.loadCheckpoint("task1");
        assertTrue(loaded.hasStageData("Stage1"));
        assertTrue(loaded.hasStageData("Stage2"));
        assertTrue(loaded.hasStageData("Stage3"));
        assertEquals("completed", loaded.getStageData("Stage1").get("status"));
        assertEquals("running", loaded.getStageData("Stage3").get("status"));
    }
}

