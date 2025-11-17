package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.event.TaskEventSink;
import xyz.firestige.executor.execution.TaskWorkerCreationContext;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.support.conflict.ConflictRegistry;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * TaskWorkerCreationContext 单元测试
 *
 * RF-02: 验证参数对象的 Builder 模式和参数验证
 */
@Tag("rf02")
@Tag("unit")
@DisplayName("TaskWorkerCreationContext 单元测试（RF-02）")
class TaskWorkerCreationContextTest {

    @Test
    @DisplayName("Builder - 成功创建（所有必需参数）")
    void testBuilder_Success() {
        TaskAggregate task = mock(TaskAggregate.class);
        List<TaskStage> stages = Collections.emptyList();
        TaskRuntimeContext runtimeContext = mock(TaskRuntimeContext.class);
        CheckpointService checkpointService = new CheckpointService(new InMemoryCheckpointStore());
        TaskEventSink eventSink = mock(TaskEventSink.class);
        TaskStateManager stateManager = mock(TaskStateManager.class);
        ConflictRegistry conflictRegistry = mock(ConflictRegistry.class);

        TaskWorkerCreationContext context = TaskWorkerCreationContext.builder()
            .planId("plan-001")
            .task(task)
            .stages(stages)
            .runtimeContext(runtimeContext)
            .checkpointService(checkpointService)
            .eventSink(eventSink)
            .stateManager(stateManager)
            .progressIntervalSeconds(15)
            .conflictRegistry(conflictRegistry)
            .build();

        assertNotNull(context);
        assertEquals("plan-001", context.getPlanId());
        assertEquals(task, context.getTask());
        assertEquals(stages, context.getStages());
        assertEquals(runtimeContext, context.getRuntimeContext());
        assertEquals(checkpointService, context.getCheckpointService());
        assertEquals(eventSink, context.getEventSink());
        assertEquals(stateManager, context.getStateManager());
        assertEquals(15, context.getProgressIntervalSeconds());
        assertEquals(conflictRegistry, context.getConflictRegistry());
    }

    @Test
    @DisplayName("Builder - 使用默认的 progressIntervalSeconds")
    void testBuilder_DefaultProgressInterval() {
        TaskAggregate task = mock(TaskAggregate.class);
        List<TaskStage> stages = Collections.emptyList();
        TaskRuntimeContext runtimeContext = mock(TaskRuntimeContext.class);
        CheckpointService checkpointService = new CheckpointService(new InMemoryCheckpointStore());
        TaskEventSink eventSink = mock(TaskEventSink.class);
        TaskStateManager stateManager = mock(TaskStateManager.class);

        TaskWorkerCreationContext context = TaskWorkerCreationContext.builder()
            .planId("plan-001")
            .task(task)
            .stages(stages)
            .runtimeContext(runtimeContext)
            .checkpointService(checkpointService)
            .eventSink(eventSink)
            .stateManager(stateManager)
            .build();

        assertNotNull(context);
        assertEquals(10, context.getProgressIntervalSeconds()); // default value
        assertNull(context.getConflictRegistry()); // optional, can be null
    }

    @Test
    @DisplayName("Builder - planId 为 null 时抛出异常")
    void testBuilder_NullPlanId() {
        TaskAggregate task = mock(TaskAggregate.class);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            TaskWorkerCreationContext.builder()
                .planId(null)
                .task(task)
                .stages(Collections.emptyList())
                .runtimeContext(mock(TaskRuntimeContext.class))
                .checkpointService(new CheckpointService(new InMemoryCheckpointStore()))
                .eventSink(mock(TaskEventSink.class))
                .stateManager(mock(TaskStateManager.class))
                .build();
        });

        assertEquals("planId is required", ex.getMessage());
    }

    @Test
    @DisplayName("Builder - planId 为空字符串时抛出异常")
    void testBuilder_EmptyPlanId() {
        TaskAggregate task = mock(TaskAggregate.class);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            TaskWorkerCreationContext.builder()
                .planId("   ")
                .task(task)
                .stages(Collections.emptyList())
                .runtimeContext(mock(TaskRuntimeContext.class))
                .checkpointService(new CheckpointService(new InMemoryCheckpointStore()))
                .eventSink(mock(TaskEventSink.class))
                .stateManager(mock(TaskStateManager.class))
                .build();
        });

        assertEquals("planId is required", ex.getMessage());
    }

    @Test
    @DisplayName("Builder - task 为 null 时抛出异常")
    void testBuilder_NullTask() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            TaskWorkerCreationContext.builder()
                .planId("plan-001")
                .task(null)
                .stages(Collections.emptyList())
                .runtimeContext(mock(TaskRuntimeContext.class))
                .checkpointService(new CheckpointService(new InMemoryCheckpointStore()))
                .eventSink(mock(TaskEventSink.class))
                .stateManager(mock(TaskStateManager.class))
                .build();
        });

        assertEquals("task is required", ex.getMessage());
    }

    @Test
    @DisplayName("Builder - stages 为 null 时抛出异常")
    void testBuilder_NullStages() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            TaskWorkerCreationContext.builder()
                .planId("plan-001")
                .task(mock(TaskAggregate.class))
                .stages(null)
                .runtimeContext(mock(TaskRuntimeContext.class))
                .checkpointService(new CheckpointService(new InMemoryCheckpointStore()))
                .eventSink(mock(TaskEventSink.class))
                .stateManager(mock(TaskStateManager.class))
                .build();
        });

        assertEquals("stages is required", ex.getMessage());
    }

    @Test
    @DisplayName("Builder - runtimeContext 为 null 时抛出异常")
    void testBuilder_NullRuntimeContext() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            TaskWorkerCreationContext.builder()
                .planId("plan-001")
                .task(mock(TaskAggregate.class))
                .stages(Collections.emptyList())
                .runtimeContext(null)
                .checkpointService(new CheckpointService(new InMemoryCheckpointStore()))
                .eventSink(mock(TaskEventSink.class))
                .stateManager(mock(TaskStateManager.class))
                .build();
        });

        assertEquals("runtimeContext is required", ex.getMessage());
    }

    @Test
    @DisplayName("Builder - checkpointService 为 null 时抛出异常")
    void testBuilder_NullCheckpointService() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            TaskWorkerCreationContext.builder()
                .planId("plan-001")
                .task(mock(TaskAggregate.class))
                .stages(Collections.emptyList())
                .runtimeContext(mock(TaskRuntimeContext.class))
                .checkpointService(null)
                .eventSink(mock(TaskEventSink.class))
                .stateManager(mock(TaskStateManager.class))
                .build();
        });

        assertEquals("checkpointService is required", ex.getMessage());
    }

    @Test
    @DisplayName("Builder - eventSink 为 null 时抛出异常")
    void testBuilder_NullEventSink() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            TaskWorkerCreationContext.builder()
                .planId("plan-001")
                .task(mock(TaskAggregate.class))
                .stages(Collections.emptyList())
                .runtimeContext(mock(TaskRuntimeContext.class))
                .checkpointService(new CheckpointService(new InMemoryCheckpointStore()))
                .eventSink(null)
                .stateManager(mock(TaskStateManager.class))
                .build();
        });

        assertEquals("eventSink is required", ex.getMessage());
    }

    @Test
    @DisplayName("Builder - stateManager 为 null 时抛出异常")
    void testBuilder_NullStateManager() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            TaskWorkerCreationContext.builder()
                .planId("plan-001")
                .task(mock(TaskAggregate.class))
                .stages(Collections.emptyList())
                .runtimeContext(mock(TaskRuntimeContext.class))
                .checkpointService(new CheckpointService(new InMemoryCheckpointStore()))
                .eventSink(mock(TaskEventSink.class))
                .stateManager(null)
                .build();
        });

        assertEquals("stateManager is required", ex.getMessage());
    }
}

