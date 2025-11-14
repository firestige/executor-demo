package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.executor.execution.PipelineResult;
import xyz.firestige.executor.execution.StageResult;
import xyz.firestige.executor.util.TimingExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PipelineResult 单元测试
 * 测试 Pipeline 结果封装类
 *
 * 预计执行时间：20 秒（2 个测试）
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("PipelineResult 单元测试")
class PipelineResultTest {

    @Test
    @DisplayName("场景 1.3.7: 创建成功的 Pipeline 结果")
    void testSuccessResult() {
        // Given: Stage 结果列表
        List<StageResult> stageResults = new ArrayList<>();
        stageResults.add(StageResult.success("Stage1"));
        stageResults.add(StageResult.success("Stage2"));
        stageResults.add(StageResult.success("Stage3"));

        // When: 创建成功结果
        PipelineResult result = PipelineResult.success(stageResults);

        // Then: 结果正确
        assertTrue(result.isSuccess(), "应该是成功的");
        assertEquals(3, result.getCompletedStages().size(), "应该有 3 个完成的 Stage");
        assertNull(result.getFailedStage(), "不应该有失败的 Stage");
    }

    @Test
    @DisplayName("场景 1.3.8: 创建包含失败 Stage 的结果")
    void testResultWithFailure() {
        // Given: Stage 结果列表，包含失败的
        List<StageResult> completedStages = new ArrayList<>();
        completedStages.add(StageResult.success("Stage1"));

        StageResult failedStage = StageResult.failure("Stage2", null);

        // When: 创建失败结果
        PipelineResult result = PipelineResult.failure(
                completedStages,
                failedStage,
                null
        );

        // Then: 结果正确
        assertFalse(result.isSuccess(), "应该是失败的");
        assertEquals(1, result.getCompletedStages().size(), "应该有 1 个成功完成的 Stage");
        assertNotNull(result.getFailedStage(), "应该有失败的 Stage");
        assertEquals("Stage2", result.getFailedStage().getStageName());
    }
}

