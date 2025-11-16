package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.Test;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.execution.DefaultTaskWorkerFactory;
import xyz.firestige.executor.execution.TaskExecutor;
import xyz.firestige.executor.execution.TaskWorkerFactory;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DefaultTaskWorkerFactoryTest {
    @Test
    void createsExecutorWithHeartbeat() {
        TaskWorkerFactory f = new DefaultTaskWorkerFactory();
        TaskStateManager sm = new TaskStateManager();
        String planId = "p";
        TaskAggregate agg = new TaskAggregate("t","p","ten");
        TaskRuntimeContext ctx = new TaskRuntimeContext("p","t","ten", null);
        sm.initializeTask(agg.getTaskId(), TaskStatus.PENDING);
        sm.registerTaskAggregate(agg.getTaskId(), agg, ctx, 0);
        TaskExecutor exec = f.create(planId, agg, List.of(), ctx, new CheckpointService(new InMemoryCheckpointStore()), new SpringTaskEventSink(sm), 5, sm, null);
        assertNotNull(exec);
        assertNull(exec.getCurrentStageName());
        // heartbeat is injected; cannot easily assert running state here without starting execution
    }
}

