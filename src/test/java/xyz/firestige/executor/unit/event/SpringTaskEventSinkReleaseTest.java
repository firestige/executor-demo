package xyz.firestige.executor.unit.event;

import org.junit.jupiter.api.Test;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.support.conflict.ConflictRegistry;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SpringTaskEventSinkReleaseTest {

    static class SpyConflictRegistry extends ConflictRegistry {
        volatile String lastReleasedTenant;
        @Override
        public void release(String tenantId) {
            super.release(tenantId);
            lastReleasedTenant = tenantId;
        }
    }

    private TaskStateManager preparedStateManager(String taskId, String tenantId) {
        TaskStateManager sm = new TaskStateManager();
        sm.initializeTask(taskId, xyz.firestige.executor.state.TaskStatus.PENDING);
        TaskAggregate agg = new TaskAggregate(taskId, "p", tenantId);
        TaskRuntimeContext ctx = new TaskRuntimeContext("p", taskId, tenantId, null);
        sm.registerTaskAggregate(taskId, agg, ctx, 1);
        return sm;
    }

    @Test
    void releaseOnCancelledEvent() {
        String taskId = "task-t-cancel-evt";
        String tenantId = "tenant-A";
        TaskStateManager sm = preparedStateManager(taskId, tenantId);
        SpyConflictRegistry spy = new SpyConflictRegistry();
        // simulate tenant lock is held
        assertTrue(spy.register(tenantId, taskId));
        SpringTaskEventSink sink = new SpringTaskEventSink(sm, spy);
        sink.publishTaskCancelled("p", taskId, 0L);
        assertEquals(tenantId, spy.lastReleasedTenant, "should release tenant lock on cancelled event");
    }

    @Test
    void releaseOnCompletedEvent() {
        String taskId = "task-t-complete-evt";
        String tenantId = "tenant-B";
        TaskStateManager sm = preparedStateManager(taskId, tenantId);
        SpyConflictRegistry spy = new SpyConflictRegistry();
        assertTrue(spy.register(tenantId, taskId));
        SpringTaskEventSink sink = new SpringTaskEventSink(sm, spy);
        sink.publishTaskCompleted("p", taskId, Duration.ZERO, List.of("s1"), 0L);
        assertEquals(tenantId, spy.lastReleasedTenant, "should release tenant lock on completed event");
    }
}
