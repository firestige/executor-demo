package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.Test;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.domain.stage.StageExecutionResult;
import xyz.firestige.executor.domain.stage.StageStep;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.execution.TaskExecutor;
import xyz.firestige.executor.execution.TaskExecutionResult;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PauseResumeCheckpointTest {

    static class SuccessStage implements TaskStage {
        private final String name;
        SuccessStage(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public boolean canSkip(TaskRuntimeContext ctx) { return false; }
        @Override public StageExecutionResult execute(TaskRuntimeContext ctx) {
            StageExecutionResult r = StageExecutionResult.start(name);
            r.finishSuccess();
            return r;
        }
        @Override public void rollback(TaskRuntimeContext ctx) {}
        @Override public List<StageStep> getSteps() { return java.util.List.of(); }
    }

    @Test
    void pauseThenResumeContinuesFromCheckpoint() {
        var store = new InMemoryCheckpointStore();
        var svc = new CheckpointService(store);
        var sm = new TaskStateManager();
        var sink = new SpringTaskEventSink(sm);

        TaskAggregate task = new TaskAggregate("t-pr", "p", "tenant-1");
        TaskRuntimeContext ctx = new TaskRuntimeContext("p", "t-pr", "tenant-1", null);
        List<TaskStage> stages = List.of(new SuccessStage("s1"), new SuccessStage("s2"), new SuccessStage("s3"));

        // Prepare state machine entries
        sm.initializeTask(task.getTaskId(), TaskStatus.PENDING);
        sm.registerTaskAggregate(task.getTaskId(), task, ctx, stages.size());

        // Request pause so executor will pause after first successful stage
        ctx.requestPause();
        TaskExecutor ex1 = new TaskExecutor("p", task, stages, ctx, svc, sink, 10, sm);
        TaskExecutionResult r1 = ex1.execute();
        assertEquals(TaskStatus.PAUSED, r1.getFinalStatus());
        assertNotNull(task.getCheckpoint());
        assertEquals(0, task.getCheckpoint().getLastCompletedStageIndex(), "checkpoint should persist after s1 completed");

        // Resume and continue from checkpoint (should start at s2)
        ctx.clearPause();
        TaskExecutor ex2 = new TaskExecutor("p", task, stages, ctx, svc, sink, 10, sm);
        TaskExecutionResult r2 = ex2.execute();
        assertEquals(TaskStatus.COMPLETED, r2.getFinalStatus());
        assertEquals(3, task.getCurrentStageIndex());
        assertNull(task.getCheckpoint(), "checkpoint should be cleared after completion");
    }
}
