package xyz.firestige.executor.unit.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.state.*;
import xyz.firestige.executor.util.TimingExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskStateMachine 单元测试
 * 测试状态机的状态转移逻辑
 *
 * 预计执行时间：60 秒（8 个测试）
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("TaskStateMachine 单元测试")
class TaskStateMachineTest {

    @Test
    @DisplayName("场景 1.1.1: 合法状态转移 - CREATED → VALIDATING")
    void testLegalStateTransition() {
        // Given: 初始状态为 CREATED
        TaskStateMachine stateMachine = new TaskStateMachine(TaskStatus.CREATED);

        // When: 转移到 VALIDATING
        StateTransitionResult result = stateMachine.transitionTo(TaskStatus.VALIDATING);

        // Then: 转移成功
        assertTrue(result.isSuccess(), "状态转移应该成功");
        assertEquals(TaskStatus.VALIDATING, stateMachine.getCurrentStatus(), "当前状态应该是 VALIDATING");
        assertEquals(TaskStatus.CREATED, result.getOldStatus(), "源状态应该是 CREATED");
        assertEquals(TaskStatus.VALIDATING, result.getNewStatus(), "目标状态应该是 VALIDATING");
    }

    @Test
    @DisplayName("场景 1.1.2: 非法状态转移被拒绝 - COMPLETED → RUNNING")
    void testIllegalStateTransition() {
        // Given: 当前状态为 COMPLETED（终态）
        TaskStateMachine stateMachine = new TaskStateMachine(TaskStatus.COMPLETED);

        // When: 尝试转移到 RUNNING
        StateTransitionResult result = stateMachine.transitionTo(TaskStatus.RUNNING);

        // Then: 转移失败，状态不变
        assertFalse(result.isSuccess(), "非法状态转移应该失败");
        assertEquals(TaskStatus.COMPLETED, stateMachine.getCurrentStatus(), "状态应该保持不变");
        assertNotNull(result.getErrorMessage(), "应该包含错误消息");
        assertTrue(result.getErrorMessage().contains("不允许"), "错误消息应该说明不允许转移");
    }

    @Test
    @DisplayName("场景 1.1.3: 状态转移历史记录")
    void testStateTransitionHistory() {
        // Given: 初始状态为 CREATED
        TaskStateMachine stateMachine = new TaskStateMachine(TaskStatus.CREATED);

        // When: 连续转移 3 次
        stateMachine.transitionTo(TaskStatus.VALIDATING);
        stateMachine.transitionTo(TaskStatus.PENDING);
        stateMachine.transitionTo(TaskStatus.RUNNING);

        // Then: 转移历史包含 3 条记录
        List<StateTransition> history = stateMachine.getTransitionHistory();
        assertEquals(3, history.size(), "应该有 3 条转移记录");

        // 验证顺序
        assertEquals(TaskStatus.CREATED, history.get(0).getFromStatus());
        assertEquals(TaskStatus.VALIDATING, history.get(0).getToStatus());

        assertEquals(TaskStatus.VALIDATING, history.get(1).getFromStatus());
        assertEquals(TaskStatus.PENDING, history.get(1).getToStatus());

        assertEquals(TaskStatus.PENDING, history.get(2).getFromStatus());
        assertEquals(TaskStatus.RUNNING, history.get(2).getToStatus());
    }

    @Test
    @DisplayName("场景 1.1.4: 带失败信息的状态转移")
    void testStateTransitionWithFailureInfo() {
        // Given: 当前状态为 RUNNING，准备失败信息
        TaskStateMachine stateMachine = new TaskStateMachine(TaskStatus.RUNNING);
        FailureInfo failureInfo = FailureInfo.of(ErrorType.SYSTEM_ERROR, "测试错误");

        // When: 转移到 FAILED 并附带失败信息
        StateTransitionResult result = stateMachine.transitionTo(TaskStatus.FAILED, failureInfo);

        // Then: 转移成功，失败信息被记录
        assertTrue(result.isSuccess(), "状态转移应该成功");
        assertEquals(TaskStatus.FAILED, stateMachine.getCurrentStatus());

        // 验证转移历史包含失败信息
        List<StateTransition> history = stateMachine.getTransitionHistory();
        assertEquals(1, history.size());
        assertNotNull(history.get(0).getFailureInfo(), "转移记录应该包含失败信息");
        assertEquals("测试错误", history.get(0).getFailureInfo().getErrorMessage());
    }

    @Test
    @DisplayName("场景 1.1.5: canTransition 验证 - 各种状态组合")
    void testCanTransition() {
        // Given: 一个状态机
        TaskStateMachine stateMachine = new TaskStateMachine(TaskStatus.CREATED);

        // When & Then: 测试各种转移组合
        assertTrue(stateMachine.canTransition(TaskStatus.CREATED, TaskStatus.VALIDATING),
                "CREATED → VALIDATING 应该允许");
        assertFalse(stateMachine.canTransition(TaskStatus.CREATED, TaskStatus.RUNNING),
                "CREATED → RUNNING 应该不允许");

        assertTrue(stateMachine.canTransition(TaskStatus.PENDING, TaskStatus.RUNNING),
                "PENDING → RUNNING 应该允许");
        assertTrue(stateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.PAUSED),
                "RUNNING → PAUSED 应该允许");
        assertTrue(stateMachine.canTransition(TaskStatus.PAUSED, TaskStatus.RESUMING),
                "PAUSED → RESUMING 应该允许");

        assertFalse(stateMachine.canTransition(TaskStatus.COMPLETED, TaskStatus.RUNNING),
                "COMPLETED → RUNNING 应该不允许");
        assertFalse(stateMachine.canTransition(TaskStatus.CANCELLED, TaskStatus.RUNNING),
                "CANCELLED → RUNNING 应该不允许");
    }

    @Test
    @DisplayName("场景 1.1.6: 完整流程 - 正常执行到完成")
    void testCompleteFlow() {
        // Given: 初始状态
        TaskStateMachine stateMachine = new TaskStateMachine(TaskStatus.CREATED);

        // When: 按正常流程转移
        assertTrue(stateMachine.transitionTo(TaskStatus.VALIDATING).isSuccess());
        assertTrue(stateMachine.transitionTo(TaskStatus.PENDING).isSuccess());
        assertTrue(stateMachine.transitionTo(TaskStatus.RUNNING).isSuccess());
        assertTrue(stateMachine.transitionTo(TaskStatus.COMPLETED).isSuccess());

        // Then: 最终状态正确
        assertEquals(TaskStatus.COMPLETED, stateMachine.getCurrentStatus());
        assertEquals(4, stateMachine.getTransitionHistory().size());
    }

    @Test
    @DisplayName("场景 1.1.7: 暂停恢复流程")
    void testPauseResumeFlow() {
        // Given: 任务正在运行
        TaskStateMachine stateMachine = new TaskStateMachine(TaskStatus.RUNNING);

        // When: 暂停 → 恢复 → 继续运行
        assertTrue(stateMachine.transitionTo(TaskStatus.PAUSED).isSuccess());
        assertEquals(TaskStatus.PAUSED, stateMachine.getCurrentStatus());

        assertTrue(stateMachine.transitionTo(TaskStatus.RESUMING).isSuccess());
        assertEquals(TaskStatus.RESUMING, stateMachine.getCurrentStatus());

        assertTrue(stateMachine.transitionTo(TaskStatus.RUNNING).isSuccess());
        assertEquals(TaskStatus.RUNNING, stateMachine.getCurrentStatus());

        // Then: 转移历史包含 3 条记录
        assertEquals(3, stateMachine.getTransitionHistory().size());
    }

    @Test
    @DisplayName("场景 1.1.8: 回滚流程")
    void testRollbackFlow() {
        // Given: 任务已完成
        TaskStateMachine stateMachine = new TaskStateMachine(TaskStatus.COMPLETED);

        // When: 触发回滚
        assertTrue(stateMachine.transitionTo(TaskStatus.ROLLING_BACK).isSuccess());
        assertEquals(TaskStatus.ROLLING_BACK, stateMachine.getCurrentStatus());

        // 回滚完成
        assertTrue(stateMachine.transitionTo(TaskStatus.ROLLED_BACK).isSuccess());
        assertEquals(TaskStatus.ROLLED_BACK, stateMachine.getCurrentStatus());

        // Then: 转移历史包含 2 条记录
        assertEquals(2, stateMachine.getTransitionHistory().size());

        // 验证 ROLLED_BACK 是终态
        assertFalse(stateMachine.canTransition(TaskStatus.ROLLED_BACK, TaskStatus.RUNNING));
    }
}

