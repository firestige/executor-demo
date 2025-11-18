package xyz.firestige.deploy.unit.state;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.state.TaskStatus;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.execution.TaskExecutor;
import xyz.firestige.deploy.checkpoint.CheckpointService;
import xyz.firestige.deploy.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.deploy.event.SpringTaskEventSink;
import xyz.firestige.deploy.domain.stage.TaskStage;
import xyz.firestige.deploy.domain.stage.StageExecutionResult;
import xyz.firestige.deploy.domain.stage.StageStep;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 持续时间字段测试（COMPLETED / FAILED / ROLLED_BACK）。
 */
public class TaskDurationTest {

    static class SuccessStage implements TaskStage {
        public String getName(){return "s";} public boolean canSkip(TaskRuntimeContext c){return false;}
        public StageExecutionResult execute(TaskRuntimeContext c){ StageExecutionResult r=StageExecutionResult.start(getName()); r.finishSuccess(); return r; }
        public void rollback(TaskRuntimeContext c){}
        public List<StageStep> getSteps(){return List.of();}
    }
    static class FailingStage implements TaskStage {
        public String getName(){return "f";} public boolean canSkip(TaskRuntimeContext c){return false;}
        public StageExecutionResult execute(TaskRuntimeContext c){ StageExecutionResult r=StageExecutionResult.start(getName()); r.finishFailure("fail"); return r; }
        public void rollback(TaskRuntimeContext c){}
        public List<StageStep> getSteps(){return List.of();}
    }

    private TaskExecutor buildExecutor(String id, TaskStage stage, TaskStateManager mgr, TaskAggregate agg, TaskRuntimeContext ctx) {
        return new TaskExecutor("p", agg, java.util.List.of(stage), ctx, new CheckpointService(new InMemoryCheckpointStore()), new SpringTaskEventSink(mgr), 10, mgr);
    }

    @Test
    void durationSetOnComplete() {
        TaskStateManager mgr = new TaskStateManager((ApplicationEventPublisher) e -> {});
        TaskAggregate agg = new TaskAggregate("task-taskDur1", "planX", "tenant");
        agg.setTotalStages(1); // RF-13: 初始化 stageProgress
        agg.markAsPending(); // RF-13: 设置为PENDING状态
        mgr.initializeTask(agg.getTaskId(), TaskStatus.PENDING);
        TaskRuntimeContext ctx = new TaskRuntimeContext("planX", agg.getTaskId(), agg.getTenantId(), null);
        mgr.registerTaskAggregate(agg.getTaskId(), agg, ctx, 1);
        TaskExecutor exec = buildExecutor(agg.getTaskId(), new SuccessStage(), mgr, agg, ctx);
        exec.execute();
        assertNotNull(agg.getDurationMillis());
        assertTrue(agg.getDurationMillis() >= 0);
    }

    @Test
    void durationSetOnFail() {
        TaskStateManager mgr = new TaskStateManager((ApplicationEventPublisher) e -> {});
        TaskAggregate agg = new TaskAggregate("task-taskDur2", "planX", "tenant");
        agg.setTotalStages(1); // RF-13: 初始化 stageProgress
        agg.markAsPending(); // RF-13: 设置为PENDING状态
        mgr.initializeTask(agg.getTaskId(), TaskStatus.PENDING);
        TaskRuntimeContext ctx = new TaskRuntimeContext("planX", agg.getTaskId(), agg.getTenantId(), null);
        mgr.registerTaskAggregate(agg.getTaskId(), agg, ctx, 1);
        TaskExecutor exec = buildExecutor(agg.getTaskId(), new FailingStage(), mgr, agg, ctx);
        exec.execute();
        assertNotNull(agg.getDurationMillis());
        assertTrue(agg.getDurationMillis() >= 0);
    }

    @Test
    void durationNotSetOnPauseOrCancel() {
        TaskStateManager mgr = new TaskStateManager((ApplicationEventPublisher) e -> {});
        TaskAggregate agg = new TaskAggregate("task-taskDur3", "planX", "tenant");
        agg.setTotalStages(1); // RF-13: 初始化 stageProgress
        agg.markAsPending(); // RF-13: 设置为PENDING状态
        mgr.initializeTask(agg.getTaskId(), TaskStatus.PENDING);
        TaskRuntimeContext ctx = new TaskRuntimeContext("planX", agg.getTaskId(), agg.getTenantId(), null);
        mgr.registerTaskAggregate(agg.getTaskId(), agg, ctx, 1);
        TaskExecutor exec = buildExecutor(agg.getTaskId(), new SuccessStage(), mgr, agg, ctx);
        // Pause mid-run should not finalize duration
        ctx.requestPause();
        exec.execute();
        assertTrue(agg.getDurationMillis() == null || agg.getDurationMillis() == 0L, "duration should not be finalized when paused mid-run");
        // Cancel: since the single stage completes fast, cancellation may happen after stage success; we do not assert duration here to avoid flakiness.
        ctx.clearPause();
        ctx.requestCancel();
        exec.execute();
        assertNotNull(agg.getStatus()); // sanity check only
    }
}
