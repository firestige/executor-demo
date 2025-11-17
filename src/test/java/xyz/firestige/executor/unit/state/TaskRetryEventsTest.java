package xyz.firestige.executor.unit.state;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.execution.TaskExecutor;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.stage.StageExecutionResult;
import xyz.firestige.executor.domain.stage.StageStep;
import xyz.firestige.executor.state.event.TaskRetryStartedEvent;
import xyz.firestige.executor.state.event.TaskRetryCompletedEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重试事件测试：验证 RetryStarted / RetryCompleted 发布及 sequenceId 递增。
 */
public class TaskRetryEventsTest {

    static class CapturingPublisher implements ApplicationEventPublisher {
        final List<Object> events = new CopyOnWriteArrayList<>();
        @Override public void publishEvent(Object event) { events.add(event); }
    }

    static class DummyStep implements StageStep {
        private final String name; DummyStep(String n){this.name=n;}
        public String getStepName(){return name;}
        public void execute(TaskRuntimeContext ctx){}
    }

    static class SingleStage implements TaskStage {
        private final String name; SingleStage(String n){this.name=n;}
        public String getName(){return name;}
        public boolean canSkip(TaskRuntimeContext ctx){return false;}
        public StageExecutionResult execute(TaskRuntimeContext ctx){ StageExecutionResult r=StageExecutionResult.start(name); r.finishSuccess(); return r; }
        public void rollback(TaskRuntimeContext ctx){}
        public List<StageStep> getSteps(){ return List.of(new DummyStep("s")); }
    }

    @Test
    void retryEventsEmittedAndSequenceIncrements() {
        CapturingPublisher pub = new CapturingPublisher();
        TaskStateManager mgr = new TaskStateManager(pub);
        TaskAggregate agg = new TaskAggregate("taskR", "planX", "tenant");
        mgr.initializeTask(agg.getTaskId(), TaskStatus.PENDING);
        TaskRuntimeContext ctx = new TaskRuntimeContext("planX", agg.getTaskId(), agg.getTenantId(), null);
        List<TaskStage> stages = List.of(new SingleStage("stage1"));
        mgr.registerTaskAggregate(agg.getTaskId(), agg, ctx, stages.size());
        SpringTaskEventSink sink = new SpringTaskEventSink(mgr);
        TaskExecutor executor = new TaskExecutor("planX", agg, stages, ctx, new CheckpointService(new InMemoryCheckpointStore()), sink, 5, mgr);
        // 初始执行
        executor.execute();
        int beforeRetryEvents = pub.events.size();
        // 重试（非 checkpoint）
        executor.retry(false);
        long retryStartedSeq = pub.events.stream().filter(e -> e instanceof TaskRetryStartedEvent).map(e -> ((TaskRetryStartedEvent)e).getSequenceId()).findFirst().orElse(-1L);
        long retryCompletedSeq = pub.events.stream().filter(e -> e instanceof TaskRetryCompletedEvent).map(e -> ((TaskRetryCompletedEvent)e).getSequenceId()).findFirst().orElse(-1L);
        assertTrue(retryStartedSeq > 0 && retryCompletedSeq > retryStartedSeq, "重试事件 sequenceId 应递增");
        assertTrue(pub.events.size() > beforeRetryEvents, "重试事件应新增");
    }
}
