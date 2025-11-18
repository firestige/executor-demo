package xyz.firestige.deploy.unit.event;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.event.SpringTaskEventSink;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.support.conflict.TenantConflictManager;

public class SpringTaskEventSinkReleaseTest {

    static class SpyConflictManager extends TenantConflictManager {
        volatile String lastReleasedTenant;
        
        public SpyConflictManager() {
            super(ConflictPolicy.FINE_GRAINED);
        }
        
        @Override
        public void releaseTask(String tenantId) {
            super.releaseTask(tenantId);
            lastReleasedTenant = tenantId;
        }
    }

    private TaskStateManager preparedStateManager(String taskId, String tenantId) {
        TaskStateManager sm = new TaskStateManager();
        sm.initializeTask(taskId, xyz.firestige.deploy.state.TaskStatus.PENDING);
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
        SpyConflictManager spy = new SpyConflictManager();
        // simulate tenant lock is held
        assertTrue(spy.registerTask(tenantId, taskId));
        SpringTaskEventSink sink = new SpringTaskEventSink(sm, spy);
        sink.publishTaskCancelled("p", taskId, 0L);
        assertEquals(tenantId, spy.lastReleasedTenant, "should release tenant lock on cancelled event");
    }

    @Test
    void releaseOnCompletedEvent() {
        String taskId = "task-t-complete-evt";
        String tenantId = "tenant-B";
        TaskStateManager sm = preparedStateManager(taskId, tenantId);
        SpyConflictManager spy = new SpyConflictManager();
        assertTrue(spy.registerTask(tenantId, taskId));
        SpringTaskEventSink sink = new SpringTaskEventSink(sm, spy);
        sink.publishTaskCompleted("p", taskId, Duration.ZERO, List.of("s1"), 0L);
        assertEquals(tenantId, spy.lastReleasedTenant, "should release tenant lock on completed event");
    }
}
