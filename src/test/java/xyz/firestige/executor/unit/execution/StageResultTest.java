package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.StageResult;
import xyz.firestige.executor.execution.StageStatus;
import xyz.firestige.executor.util.TimingExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StageResult 单元测试
 * 测试 Stage 结果封装类
 *
 * 预计执行时间：20 秒（3 个测试）
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("StageResult 单元测试")
class StageResultTest {

    @Test
    @DisplayName("场景 1.3.4: 创建成功的 Stage 结果")
    void testSuccessResult() {
        // When: 创建成功结果
        StageResult result = StageResult.success("TestStage");

        // Then: 结果正确
        assertTrue(result.isSuccess(), "应该是成功的");
        assertEquals("TestStage", result.getStageName(), "Stage 名称应该正确");
        assertEquals(StageStatus.COMPLETED, result.getStatus(), "状态应该是 COMPLETED");
        assertNull(result.getFailureInfo(), "成功结果不应该有失败信息");
    }

    @Test
    @DisplayName("场景 1.3.5: 创建失败的 Stage 结果")
    void testFailureResult() {
        // Given: 失败信息
        FailureInfo failureInfo = FailureInfo.of(ErrorType.SYSTEM_ERROR, "测试错误");

        // When: 创建失败结果
        StageResult result = StageResult.failure("TestStage", failureInfo);

        // Then: 结果正确
        assertFalse(result.isSuccess(), "应该是失败的");
        assertEquals("TestStage", result.getStageName());
        assertEquals(StageStatus.FAILED, result.getStatus(), "状态应该是 FAILED");
        assertNotNull(result.getFailureInfo(), "失败结果应该有失败信息");
        assertEquals("测试错误", result.getFailureInfo().getErrorMessage());
    }

    @Test
    @DisplayName("场景 1.3.6: 计算执行时长")
    void testCalculateDuration() {
        // Given: 创建结果并设置时间
        StageResult result = StageResult.success("TestStage");

        LocalDateTime startTime = LocalDateTime.now().minusSeconds(1); // 1秒前
        LocalDateTime endTime = LocalDateTime.now();

        result.setStartTime(startTime);
        result.setEndTime(endTime);

        // When: 计算时长
        result.calculateDuration();

        // Then: 时长正确
        assertNotNull(result.getDuration(), "时长不应该为 null");
        long millis = result.getDuration().toMillis();
        assertTrue(millis >= 900 && millis <= 1100,
                "时长应该约为 1000ms，实际: " + millis + "ms");
    }
}

