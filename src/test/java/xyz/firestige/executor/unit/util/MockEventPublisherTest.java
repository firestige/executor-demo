package xyz.firestige.executor.unit.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEvent;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.state.event.TaskCreatedEvent;
import xyz.firestige.executor.state.event.TaskStartedEvent;
import xyz.firestige.executor.util.MockEventPublisher;
import xyz.firestige.executor.util.TimingExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockEventPublisher 单元测试
 * 测试Mock事件发布器
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("MockEventPublisher 单元测试")
class MockEventPublisherTest {

    private MockEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new MockEventPublisher();
    }

    @Test
    @DisplayName("场景: 发布事件")
    void testPublishEvent() {
        // Given: 事件
        TaskCreatedEvent event = new TaskCreatedEvent("task1", 5,
            xyz.firestige.executor.orchestration.ExecutionMode.CONCURRENT);

        // When: 发布事件
        publisher.publishEvent(event);

        // Then: 事件被记录
        assertEquals(1, publisher.getEventCount());
        assertTrue(publisher.hasEvent(TaskCreatedEvent.class));
    }

    @Test
    @DisplayName("场景: 发布多个事件")
    void testPublishMultipleEvents() {
        // Given: 多个事件
        TaskCreatedEvent event1 = new TaskCreatedEvent("task1", 5,
            xyz.firestige.executor.orchestration.ExecutionMode.CONCURRENT);
        TaskStartedEvent event2 = new TaskStartedEvent("task1", 3);

        // When: 发布多个事件
        publisher.publishEvent(event1);
        publisher.publishEvent(event2);

        // Then: 都被记录
        assertEquals(2, publisher.getEventCount());
        assertTrue(publisher.hasEvent(TaskCreatedEvent.class));
        assertTrue(publisher.hasEvent(TaskStartedEvent.class));
    }

    @Test
    @DisplayName("场景: 获取特定类型的事件")
    void testGetEventsByType() {
        // Given: 发布多个事件
        TaskCreatedEvent event1 = new TaskCreatedEvent("task1", 5,
            xyz.firestige.executor.orchestration.ExecutionMode.CONCURRENT);
        TaskCreatedEvent event2 = new TaskCreatedEvent("task2", 3,
            xyz.firestige.executor.orchestration.ExecutionMode.FIFO);
        TaskStartedEvent event3 = new TaskStartedEvent("task1", 3);

        publisher.publishEvent(event1);
        publisher.publishEvent(event2);
        publisher.publishEvent(event3);

        // When: 获取特定类型的事件
        List<TaskCreatedEvent> createdEvents = publisher.getEventsOfType(TaskCreatedEvent.class);
        List<TaskStartedEvent> startedEvents = publisher.getEventsOfType(TaskStartedEvent.class);

        // Then: 类型正确
        assertEquals(2, createdEvents.size());
        assertEquals(1, startedEvents.size());
    }

    @Test
    @DisplayName("场景: 清空事件")
    void testClearEvents() {
        // Given: 发布一些事件
        publisher.publishEvent(new TaskCreatedEvent("task1", 5,
            xyz.firestige.executor.orchestration.ExecutionMode.CONCURRENT));
        publisher.publishEvent(new TaskStartedEvent("task1", 3));

        assertEquals(2, publisher.getEventCount());

        // When: 清空事件
        publisher.clear();

        // Then: 事件被清空
        assertEquals(0, publisher.getEventCount());
        assertFalse(publisher.hasEvent(TaskCreatedEvent.class));
    }

    @Test
    @DisplayName("场景: 获取所有事件")
    void testGetAllEvents() {
        // Given: 发布多个事件
        publisher.publishEvent(new TaskCreatedEvent("task1", 5,
            xyz.firestige.executor.orchestration.ExecutionMode.CONCURRENT));
        publisher.publishEvent(new TaskStartedEvent("task1", 3));

        // When: 获取所有事件
        List<Object> allEvents = publisher.getPublishedEvents();

        // Then: 包含所有事件
        assertEquals(2, allEvents.size());
    }
}

