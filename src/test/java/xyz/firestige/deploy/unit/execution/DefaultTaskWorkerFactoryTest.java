package xyz.firestige.deploy.unit.execution;

import org.junit.jupiter.api.Test;
import xyz.firestige.deploy.checkpoint.CheckpointService;
import xyz.firestige.deploy.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.event.SpringTaskEventSink;
import xyz.firestige.deploy.execution.DefaultTaskWorkerFactory;
import xyz.firestige.deploy.execution.TaskExecutor;
import xyz.firestige.deploy.execution.TaskWorkerCreationContext;
import xyz.firestige.deploy.execution.TaskWorkerFactory;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.state.TaskStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DefaultTaskWorkerFactoryTest {
    @Test
    void createsExecutorWithHeartbeat() {
        TaskStateManager sm = new TaskStateManager();
        CheckpointService checkpointService = new CheckpointService(new InMemoryCheckpointStore());
        SpringTaskEventSink eventSink = new SpringTaskEventSink(sm);
        
        // RF-17: 基础设施依赖通过构造器注入
        TaskWorkerFactory f = new DefaultTaskWorkerFactory(
            checkpointService,
            eventSink,
            sm,
            null,  // conflictManager
            5,     // progressIntervalSeconds
            null   // metrics
        );
        
        String planId = "p";
        TaskAggregate agg = new TaskAggregate("task-t","p","ten");
        TaskRuntimeContext ctx = new TaskRuntimeContext("p","t","ten", null);
        sm.initializeTask(agg.getTaskId(), TaskStatus.PENDING);
        sm.registerTaskAggregate(agg.getTaskId(), agg, ctx, 0);
        
        // RF-17: Context 只包含领域数据
        TaskWorkerCreationContext c = TaskWorkerCreationContext.builder()
            .planId(planId)
            .task(agg)
            .stages(List.of())
            .runtimeContext(ctx)
            .existingExecutor(null)
            .build();
        TaskExecutor exec = f.create(c);
        assertNotNull(exec);
        assertNull(exec.getCurrentStageName());
        // heartbeat is injected; cannot easily assert running state here without starting execution
    }
}

