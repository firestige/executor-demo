package xyz.firestige.deploy.unit.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.deploy.exception.ErrorType;
import xyz.firestige.deploy.exception.FailureInfo;
import xyz.firestige.deploy.orchestration.ExecutionMode;
import xyz.firestige.deploy.state.TaskStatus;
import xyz.firestige.deploy.state.event.TaskCreatedEvent;
import xyz.firestige.deploy.state.event.TaskFailedEvent;
import xyz.firestige.deploy.state.event.TaskStartedEvent;
import xyz.firestige.deploy.util.TimingExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 事件类单元测试 - 简化版
 * 测试核心事件的基本功能
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("事件类单元测试")
class TaskEventTest {

    @Test
    @DisplayName("场景: 事件自动生成唯一ID")
    void testEventIdGeneration() {
        TaskCreatedEvent event1 = new TaskCreatedEvent("task1", 5, ExecutionMode.CONCURRENT);
        TaskCreatedEvent event2 = new TaskCreatedEvent("task2", 3, ExecutionMode.FIFO);

        assertNotNull(event1.getEventId());
        assertNotNull(event2.getEventId());
        assertNotEquals(event1.getEventId(), event2.getEventId());
    }

    @Test
    @DisplayName("场景: TaskCreatedEvent包含正确属性")
    void testTaskCreatedEvent() {
        TaskCreatedEvent event = new TaskCreatedEvent("task1", 5, ExecutionMode.CONCURRENT);

        assertEquals("task1", event.getTaskId());
        assertEquals(5, event.getConfigCount());
        assertEquals(ExecutionMode.CONCURRENT, event.getExecutionMode());
        assertEquals(TaskStatus.CREATED, event.getStatus());
    }

    @Test
    @DisplayName("场景: TaskStartedEvent包含正确属性")
    void testTaskStartedEvent() {
        TaskStartedEvent event = new TaskStartedEvent("task1", 5);

        assertEquals("task1", event.getTaskId());
        assertEquals(5, event.getTotalStages());
        assertEquals(TaskStatus.RUNNING, event.getStatus());
        assertNotNull(event.getTimestamp());
    }

    @Test
    @DisplayName("场景: TaskFailedEvent包含失败信息")
    void testTaskFailedEvent() {
        FailureInfo failureInfo = FailureInfo.of(ErrorType.SYSTEM_ERROR, "系统错误");
        TaskFailedEvent event = new TaskFailedEvent("task1", failureInfo, List.of(), "Stage2");

        assertEquals("task1", event.getTaskId());
        assertNotNull(event.getFailureInfo());
        assertEquals("系统错误", event.getFailureInfo().getErrorMessage());
        assertEquals(TaskStatus.FAILED, event.getStatus());
    }
}

