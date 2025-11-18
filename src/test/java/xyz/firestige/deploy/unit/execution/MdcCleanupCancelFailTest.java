package xyz.firestige.deploy.unit.execution;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import xyz.firestige.deploy.checkpoint.CheckpointService;
import xyz.firestige.deploy.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.deploy.domain.stage.TaskStage;
import xyz.firestige.deploy.domain.stage.StageExecutionResult;
import xyz.firestige.deploy.domain.stage.StageStep;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.event.SpringTaskEventSink;
import xyz.firestige.deploy.execution.TaskExecutor;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.state.TaskStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MdcCleanupCancelFailTest {

    static class FailingStage implements TaskStage {
        public String getName(){return "f";}
        public boolean canSkip(TaskRuntimeContext c){return false;}
        public StageExecutionResult execute(TaskRuntimeContext c){ StageExecutionResult r=StageExecutionResult.start(getName()); r.finishFailure("x"); return r; }
        public void rollback(TaskRuntimeContext c){}
        public List<StageStep> getSteps(){return List.of();}
    }

    static class CancellableStage implements TaskStage {
        public String getName(){return "c";}
        public boolean canSkip(TaskRuntimeContext c){return false;}
        public StageExecutionResult execute(TaskRuntimeContext c){ c.requestCancel(); StageExecutionResult r=StageExecutionResult.start(getName()); r.finishSuccess(); return r; }
        public void rollback(TaskRuntimeContext c){}
        public List<StageStep> getSteps(){return List.of();}
    }

    @Test
    void mdcClearedAfterFailure() {
        TaskStateManager sm = new TaskStateManager(e -> {});
        TaskAggregate agg = new TaskAggregate("task-t-mdc-f","p","tenant");
        sm.initializeTask(agg.getTaskId(), TaskStatus.PENDING);
        TaskRuntimeContext ctx = new TaskRuntimeContext("p","t-mdc-f","tenant", null);
        sm.registerTaskAggregate(agg.getTaskId(), agg, ctx, 1);
        TaskExecutor exec = new TaskExecutor("p", agg, List.of(new FailingStage()), ctx, new CheckpointService(new InMemoryCheckpointStore()), new SpringTaskEventSink(sm), 5, sm);
        exec.execute();
        assertTrue(MDC.getCopyOfContextMap() == null || MDC.getCopyOfContextMap().isEmpty(), "MDC should be cleared after failure");
    }

    @Test
    void mdcClearedAfterCancel() {
        TaskStateManager sm = new TaskStateManager(e -> {});
        TaskAggregate agg = new TaskAggregate("task-t-mdc-c","p","tenant");
        sm.initializeTask(agg.getTaskId(), TaskStatus.PENDING);
        TaskRuntimeContext ctx = new TaskRuntimeContext("p","t-mdc-c","tenant", null);
        sm.registerTaskAggregate(agg.getTaskId(), agg, ctx, 1);
        TaskExecutor exec = new TaskExecutor("p", agg, List.of(new CancellableStage()), ctx, new CheckpointService(new InMemoryCheckpointStore()), new SpringTaskEventSink(sm), 5, sm);
        exec.execute();
        assertTrue(MDC.getCopyOfContextMap() == null || MDC.getCopyOfContextMap().isEmpty(), "MDC should be cleared after cancel");
    }
}

