package xyz.firestige.executor.unit.state;

import org.junit.jupiter.api.Test;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.domain.task.TenantDeployConfigSnapshot;
import xyz.firestige.executor.service.health.HealthCheckClient;
import xyz.firestige.executor.service.health.VersionRollbackHealthVerifier;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TaskRollbackHealthVerifierTest {

    static class MockClient implements HealthCheckClient {
        private final long version;
        MockClient(long version) { this.version = version; }
        @Override
        public Map<String, Object> get(String endpoint) {
            return Map.of("version", version);
        }
    }

    @Test
    void rollbackHealthVerifierUpdatesLastKnownGoodVersionOnSuccess() {
        TaskStateManager mgr = new TaskStateManager();
        String taskId = "t-rb-ok";
        mgr.initializeTask(taskId, TaskStatus.PENDING);
        TaskAggregate agg = new TaskAggregate(taskId, "plan-x", "tenant-1");
        TenantDeployConfigSnapshot snap = new TenantDeployConfigSnapshot("tenant-1", 10L, 5L, "du-1", List.of("http://ep1"));
        agg.setPrevConfigSnapshot(snap);
        TaskRuntimeContext ctx = new TaskRuntimeContext("plan-x", taskId, "tenant-1", null);
        mgr.registerTaskAggregate(taskId, agg, ctx, 1);
        mgr.setRollbackHealthVerifier(new VersionRollbackHealthVerifier(new MockClient(5L), "version"));
        // valid transition path
        mgr.updateState(taskId, TaskStatus.RUNNING);
        mgr.updateState(taskId, TaskStatus.ROLLING_BACK);
        mgr.updateState(taskId, TaskStatus.ROLLED_BACK);
        assertEquals(5L, agg.getLastKnownGoodVersion(), "Should set lastKnownGoodVersion to snapshot version when health passes");
    }

    @Test
    void rollbackHealthVerifierDoesNotUpdateOnFailure() {
        TaskStateManager mgr = new TaskStateManager();
        String taskId = "t-rb-fail";
        mgr.initializeTask(taskId, TaskStatus.PENDING);
        TaskAggregate agg = new TaskAggregate(taskId, "plan-x", "tenant-1");
        TenantDeployConfigSnapshot snap = new TenantDeployConfigSnapshot("tenant-1", 10L, 5L, "du-1", List.of("http://ep1"));
        agg.setPrevConfigSnapshot(snap);
        TaskRuntimeContext ctx = new TaskRuntimeContext("plan-x", taskId, "tenant-1", null);
        mgr.registerTaskAggregate(taskId, agg, ctx, 1);
        mgr.setRollbackHealthVerifier(new VersionRollbackHealthVerifier(new MockClient(4L), "version"));
        mgr.updateState(taskId, TaskStatus.RUNNING);
        mgr.updateState(taskId, TaskStatus.ROLLING_BACK);
        mgr.updateState(taskId, TaskStatus.ROLLED_BACK);
        assertNull(agg.getLastKnownGoodVersion(), "Should not update lastKnownGoodVersion when health check fails");
    }
}
