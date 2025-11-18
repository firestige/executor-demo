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
import xyz.firestige.deploy.state.event.TaskRollingBackEvent;
import xyz.firestige.deploy.state.event.TaskRollbackFailedEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回滚阶段事件测试：验证 per-stage 事件按成功/失败发布。
 */
public class TaskRollbackStageEventsTest {

    static class CapturingPublisher implements ApplicationEventPublisher { final List<Object> events = new CopyOnWriteArrayList<>(); @Override public void publishEvent(Object e){ events.add(e);} }

    static class SuccessStage implements TaskStage {
        public String getName(){return "success-stage";} public boolean canSkip(TaskRuntimeContext c){return false;}
        public StageExecutionResult execute(TaskRuntimeContext c){ StageExecutionResult r=StageExecutionResult.start(getName()); r.finishSuccess(); return r; }
        public void rollback(TaskRuntimeContext c){}
        public List<StageStep> getSteps(){return List.of();}
    }
    static class FailingStage implements TaskStage {
        public String getName(){return "failing-stage";} public boolean canSkip(TaskRuntimeContext c){return false;}
        public StageExecutionResult execute(TaskRuntimeContext c){ StageExecutionResult r=StageExecutionResult.start(getName()); r.finishSuccess(); return r; }
        public void rollback(TaskRuntimeContext c){throw new RuntimeException("boom");}
        public List<StageStep> getSteps(){return List.of();}
    }

    @Test
    void rollbackEmitsStageEvents() {
        CapturingPublisher pub = new CapturingPublisher();
        TaskStateManager mgr = new TaskStateManager(pub);
        TaskAggregate agg = new TaskAggregate("task-taskRB", "planX", "tenant");
        mgr.initializeTask(agg.getTaskId(), TaskStatus.PENDING);
        TaskRuntimeContext ctx = new TaskRuntimeContext("planX", agg.getTaskId(), agg.getTenantId(), null);
        List<TaskStage> stages = List.of(new SuccessStage(), new FailingStage());
        mgr.registerTaskAggregate(agg.getTaskId(), agg, ctx, stages.size());
        SpringTaskEventSink sink = new SpringTaskEventSink(mgr);
        TaskExecutor executor = new TaskExecutor("planX", agg, stages, ctx, new CheckpointService(new InMemoryCheckpointStore()), sink, 5, mgr);
        executor.execute(); // 完成后运行回滚
        executor.rollback();
        List<Object> rollingBackEvents = pub.events.stream().filter(e -> e instanceof TaskRollingBackEvent).collect(Collectors.toList());
        assertFalse(rollingBackEvents.isEmpty(), "应有 RollingBack 事件");
        boolean hasRollbackFailed = pub.events.stream().anyMatch(e -> e instanceof TaskRollbackFailedEvent);
        assertTrue(hasRollbackFailed, "失败 Stage 的回滚应触发 RollbackFailed 事件");
    }
}
