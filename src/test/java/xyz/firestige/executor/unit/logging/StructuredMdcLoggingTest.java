package xyz.firestige.executor.unit.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.stage.StageExecutionResult;
import xyz.firestige.executor.domain.stage.StageStep;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.execution.TaskExecutor;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StructuredMdcLoggingTest {

    static class InspectMdcStage implements TaskStage {
        public String getName(){return "mdc-stage";}
        public boolean canSkip(TaskRuntimeContext c){return false;}
        public StageExecutionResult execute(TaskRuntimeContext c){
            c.injectMdc(getName());
            assertEquals("p", MDC.get("planId"));
            assertEquals("t-log", MDC.get("taskId"));
            assertEquals("tenant", MDC.get("tenantId"));
            assertEquals("mdc-stage", MDC.get("stageName"));
            StageExecutionResult r = StageExecutionResult.start(getName()); r.finishSuccess(); return r;
        }
        public void rollback(TaskRuntimeContext c){}
        public List<StageStep> getSteps(){return List.of();}
    }

    @Test
    void mdcFieldsPresentDuringStage() {
        TaskStateManager sm = new TaskStateManager(e -> {});
        TaskAggregate agg = new TaskAggregate("t-log","p","tenant");
        sm.initializeTask(agg.getTaskId(), TaskStatus.PENDING);
        TaskRuntimeContext ctx = new TaskRuntimeContext("p","t-log","tenant", null);
        sm.registerTaskAggregate(agg.getTaskId(), agg, ctx, 1);
        TaskExecutor exec = new TaskExecutor("p", agg, List.of(new InspectMdcStage()), ctx, new CheckpointService(new InMemoryCheckpointStore()), new SpringTaskEventSink(sm), 5, sm);
        exec.execute();
        assertTrue(MDC.getCopyOfContextMap() == null || MDC.getCopyOfContextMap().isEmpty());
    }
}

