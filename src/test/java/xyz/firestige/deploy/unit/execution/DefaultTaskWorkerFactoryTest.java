package xyz.firestige.deploy.unit.execution;

import org.junit.jupiter.api.Test;
import xyz.firestige.deploy.checkpoint.CheckpointService;
import xyz.firestige.deploy.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.event.SpringTaskEventSink;
import xyz.firestige.deploy.execution.DefaultTaskWorkerFactory;
import xyz.firestige.deploy.execution.TaskExecutor;
import xyz.firestige.deploy.execution.TaskWorkerFactory;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.state.TaskStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DefaultTaskWorkerFactoryTest {
    @Test
    void createsExecutorWithHeartbeat() {
        TaskWorkerFactory f = new DefaultTaskWorkerFactory();
        TaskStateManager sm = new TaskStateManager();
        String planId = "p";
        TaskAggregate agg = new TaskAggregate("task-t","p","ten");
        TaskRuntimeContext ctx = new TaskRuntimeContext("p","t","ten", null);
        sm.initializeTask(agg.getTaskId(), TaskStatus.PENDING);
        sm.registerTaskAggregate(agg.getTaskId(), agg, ctx, 0);
        TaskExecutor exec = f.create(planId, agg, List.of(), ctx, new CheckpointService(new InMemoryCheckpointStore()), new SpringTaskEventSink(sm), 5, sm, null);
        assertNotNull(exec);
        assertNull(exec.getCurrentStageName());
        // heartbeat is injected; cannot easily assert running state here without starting execution
    }
}

