package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.PipelineResult;
import xyz.firestige.executor.execution.StageResult;
import xyz.firestige.executor.execution.pipeline.Pipeline;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.execution.pipeline.PipelineStage;
import xyz.firestige.executor.execution.pipeline.InMemoryCheckpointManager;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.util.TimingExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pipeline 单元测试
 * 测试 Pipeline 的核心执行逻辑
 *
 * 预计执行时间：300 秒（8 个测试）
 */
@Tag("unit")
@Tag("standard")
@ExtendWith(TimingExtension.class)
@DisplayName("Pipeline 单元测试")
class PipelineTest {

    private Pipeline pipeline;
    private PipelineContext context;

    @BeforeEach
    void setUp() {
        pipeline = new Pipeline();
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        context = new PipelineContext("task1", config);
    }

    @Test
    @DisplayName("场景: 顺序执行所有 Stage")
    void testSequentialExecution() {
        // Given: 3 个 Mock Stage
        PipelineStage stage1 = createMockStage("Stage1", 10, true);
        PipelineStage stage2 = createMockStage("Stage2", 20, true);
        PipelineStage stage3 = createMockStage("Stage3", 30, true);

        pipeline.addStage(stage1);
        pipeline.addStage(stage2);
        pipeline.addStage(stage3);

        // When: 执行 Pipeline
        PipelineResult result = pipeline.execute(context);

        // Then: 所有 Stage 都执行了
        assertTrue(result.isSuccess(), "Pipeline 应该执行成功");
        verify(stage1, times(1)).execute(any(PipelineContext.class));
        verify(stage2, times(1)).execute(any(PipelineContext.class));
        verify(stage3, times(1)).execute(any(PipelineContext.class));

        assertEquals(3, result.getCompletedStages().size(), "应该完成 3 个 Stage");
    }

    @Test
    @DisplayName("场景: Stage 失败后停止执行")
    void testStopOnStageFailure() {
        // Given: 3 个 Stage，第 2 个失败
        PipelineStage stage1 = createMockStage("Stage1", 10, true);
        PipelineStage stage2 = createMockStage("Stage2", 20, false); // 失败
        PipelineStage stage3 = createMockStage("Stage3", 30, true);

        pipeline.addStage(stage1);
        pipeline.addStage(stage2);
        pipeline.addStage(stage3);

        // When: 执行 Pipeline
        PipelineResult result = pipeline.execute(context);

        // Then: 第 3 个 Stage 不执行
        assertFalse(result.isSuccess(), "Pipeline 应该失败");
        verify(stage1, times(1)).execute(any(PipelineContext.class));
        verify(stage2, times(1)).execute(any(PipelineContext.class));
        verify(stage3, never()).execute(any(PipelineContext.class)); // 不应该执行
    }

    @Test
    @DisplayName("场景: 按 order 排序执行")
    void testStageOrderExecution() {
        // Given: 添加 Stage 的顺序与执行顺序不同
        PipelineStage stage30 = createMockStage("Stage30", 30, true);
        PipelineStage stage10 = createMockStage("Stage10", 10, true);
        PipelineStage stage20 = createMockStage("Stage20", 20, true);

        // 乱序添加
        pipeline.addStage(stage30);
        pipeline.addStage(stage10);
        pipeline.addStage(stage20);

        // When: 执行 Pipeline
        PipelineResult result = pipeline.execute(context);

        // Then: 按 order 顺序执行
        assertTrue(result.isSuccess());
        List<StageResult> completedStages = result.getCompletedStages();
        assertEquals(3, completedStages.size());

        // 验证执行顺序
        assertEquals("Stage10", completedStages.get(0).getStageName());
        assertEquals("Stage20", completedStages.get(1).getStageName());
        assertEquals("Stage30", completedStages.get(2).getStageName());
    }

    @Test
    @DisplayName("场景: 暂停时保存检查点")
    void testSaveCheckpointOnPause() {
        // Given: 配置检查点管理器
        InMemoryCheckpointManager checkpointManager = new InMemoryCheckpointManager();
        pipeline.setCheckpointManager(checkpointManager);

        PipelineStage stage1 = createMockStage("Stage1", 10, true);
        PipelineStage stage2 = createMockStage("Stage2", 20, true);

        pipeline.addStage(stage1);
        pipeline.addStage(stage2);

        // When: 执行一个 Stage 后暂停
        // 模拟在第一个 Stage 后暂停
        when(stage1.execute(any())).thenAnswer(invocation -> {
            PipelineContext ctx = invocation.getArgument(0);
            ctx.requestPause(); // 执行后请求暂停
            return StageResult.success("Stage1");
        });

        pipeline.execute(context);

        // Then: 检查点应该被保存
        assertTrue(checkpointManager.hasCheckpoint("task1"), "应该保存检查点");
    }

    @Test
    @DisplayName("场景: 取消时停止执行")
    void testStopOnCancel() {
        // Given: 3 个 Stage
        PipelineStage stage1 = createMockStage("Stage1", 10, true);
        PipelineStage stage2 = createMockStage("Stage2", 20, true);
        PipelineStage stage3 = createMockStage("Stage3", 30, true);

        pipeline.addStage(stage1);
        pipeline.addStage(stage2);
        pipeline.addStage(stage3);

        // When: 在第一个 Stage 后取消
        when(stage1.execute(any())).thenAnswer(invocation -> {
            PipelineContext ctx = invocation.getArgument(0);
            ctx.requestCancel();
            return StageResult.success("Stage1");
        });

        pipeline.execute(context);

        // Then: 后续 Stage 不执行
        verify(stage1, times(1)).execute(any());
        verify(stage2, never()).execute(any());
        verify(stage3, never()).execute(any());
        // 注意：取消可能不会标记为失败，只是提前终止
    }

    @Test
    @DisplayName("场景: 空 Pipeline 返回成功")
    void testEmptyPipelineSuccess() {
        // Given: 没有 Stage 的 Pipeline

        // When: 执行
        PipelineResult result = pipeline.execute(context);

        // Then: 返回成功
        assertTrue(result.isSuccess(), "空 Pipeline 应该成功");
        assertTrue(result.getCompletedStages().isEmpty(), "不应该有完成的 Stage");
    }

    @Test
    @DisplayName("场景: Stage 异常处理")
    void testStageExceptionHandling() {
        // Given: 一个会抛异常的 Stage
        PipelineStage stage1 = createMockStage("Stage1", 10, true);
        PipelineStage stage2 = mock(PipelineStage.class);
        when(stage2.getName()).thenReturn("Stage2");
        when(stage2.getOrder()).thenReturn(20);
        when(stage2.execute(any())).thenThrow(new RuntimeException("测试异常"));

        pipeline.addStage(stage1);
        pipeline.addStage(stage2);

        // When: 执行 Pipeline
        PipelineResult result = pipeline.execute(context);

        // Then: Pipeline 失败，但不会崩溃
        assertFalse(result.isSuccess(), "Pipeline 应该失败");
        assertNotNull(result.getFailedStage(), "应该有失败的 Stage");
    }

    // ===== 辅助方法 =====

    private PipelineStage createMockStage(String name, int order, boolean success) {
        PipelineStage stage = mock(PipelineStage.class);
        when(stage.getName()).thenReturn(name);
        when(stage.getOrder()).thenReturn(order);
        when(stage.supportsRollback()).thenReturn(false);

        if (success) {
            when(stage.execute(any())).thenReturn(StageResult.success(name));
        } else {
            FailureInfo failureInfo = FailureInfo.of(ErrorType.SYSTEM_ERROR, name + " failed");
            when(stage.execute(any())).thenReturn(StageResult.failure(name, failureInfo));
        }

        // rollback 是 void 方法，使用 doNothing
        doNothing().when(stage).rollback(any());

        return stage;
    }
}

