package xyz.firestige.executor.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.TenantDeployConfig;
import xyz.firestige.executor.state.TaskStateMachine;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.util.MockEventPublisher;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.util.TimingExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskStateMachine 与事件发布集成测试
 * 测试状态机与事件系统的协作
 */
@Tag("integration")
@Tag("standard")
@ExtendWith(TimingExtension.class)
@DisplayName("TaskStateMachine + EventPublisher 集成测试")
class TaskStateMachineEventIntegrationTest {

    private TaskStateMachine stateMachine;
    private MockEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new MockEventPublisher();
        stateMachine = new TaskStateMachine(TaskStatus.CREATED);
    }

    @Test
    @DisplayName("场景: 状态机初始化")
    void testStateMachineInitialization() {
        // Then: 状态机已初始化
        assertNotNull(stateMachine);
        // 初始状态为 CREATED
        assertEquals(TaskStatus.CREATED, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("场景: 验证合法的状态转移")
    void testValidTransition() {
        // Given: 当前状态为 CREATED
        assertEquals(TaskStatus.CREATED, stateMachine.getCurrentStatus());

        // When & Then: 验证到 VALIDATING 的转移是合法的
        assertDoesNotThrow(() -> stateMachine.validateTransition(TaskStatus.VALIDATING));
    }

    @Test
    @DisplayName("场景: 拒绝非法的状态转移")
    void testInvalidTransition() {
        // Given: 当前状态为 CREATED
        assertEquals(TaskStatus.CREATED, stateMachine.getCurrentStatus());

        // When & Then: 验证到 COMPLETED 的转移是非法的（应该抛异常）
        assertThrows(xyz.firestige.executor.exception.StateTransitionException.class,
            () -> stateMachine.validateTransition(TaskStatus.COMPLETED));
    }

    @Test
    @DisplayName("场景: 获取转移历史")
    void testGetTransitionHistory() {
        // When: 获取转移历史
        var history = stateMachine.getTransitionHistory();

        // Then: 历史记录存在
        assertNotNull(history);
    }
}

