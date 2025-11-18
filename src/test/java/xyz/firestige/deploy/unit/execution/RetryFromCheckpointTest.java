package xyz.firestige.deploy.unit.execution;

import org.junit.jupiter.api.Test;
import xyz.firestige.deploy.checkpoint.CheckpointService;
import xyz.firestige.deploy.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.deploy.domain.stage.StageStep;
import xyz.firestige.deploy.domain.stage.TaskStage;
import xyz.firestige.deploy.domain.stage.StageExecutionResult;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.event.SpringTaskEventSink;
import xyz.firestige.deploy.execution.TaskExecutor;
import xyz.firestige.deploy.state.TaskStateManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RetryFromCheckpointTest {

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
        @Override public List<StageStep> getSteps() { return List.of(); }
    }

    @Test
    void fromCheckpointSkipsCompletedStages() {
        var store = new InMemoryCheckpointStore();
        var svc = new CheckpointService(store);
        var state = new TaskStateManager();
        var sink = new SpringTaskEventSink(state);
        TaskAggregate t = new TaskAggregate("task-t-ck", "p", "ten");
        TaskRuntimeContext ctx = new TaskRuntimeContext("p","t-ck","ten", null);
        List<TaskStage> stages = List.of(new SuccessStage("s1"), new SuccessStage("s2"), new SuccessStage("s3"));
        // 先执行一次，走到末尾，checkpoint 清理
        TaskExecutor ex = new TaskExecutor("p", t, stages, ctx, svc, sink, 10, state);
        ex.execute();
        assertNull(t.getCheckpoint());
        // 再执行一次但先人为保存检查点到 s1 完成
        svc.saveCheckpoint(t, java.util.List.of("s1"), 0);
        // fresh 重试：应清除 checkpoint 从头执行
        var exFresh = new TaskExecutor("p", t, stages, ctx, svc, sink, 10, state);
        exFresh.retry(false);
        assertNull(t.getCheckpoint());
        // fromCheckpoint 重试：应跳过 s1，从 s2 开始
        svc.saveCheckpoint(t, java.util.List.of("s1"), 0);
        var exCp = new TaskExecutor("p", t, stages, ctx, svc, sink, 10, state);
        var res = exCp.retry(true);
        // Should only execute remaining 2 stages (s2, s3) in this run
        assertEquals(2, res.getCompletedStages().size());
        // And overall task should advance to the end
        assertEquals(3, t.getCurrentStageIndex());
        assertNull(t.getCheckpoint());
    }
}
