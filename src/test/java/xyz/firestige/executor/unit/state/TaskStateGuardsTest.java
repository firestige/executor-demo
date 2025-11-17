package xyz.firestige.executor.unit.state;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guard 测试：验证 FAILED->RUNNING, RUNNING->PAUSED, RUNNING->COMPLETED 迁移约束
 */
public class TaskStateGuardsTest {

    @Test
    void failedToRunningGuardBlocksWhenRetryCountAtMax() {
        TaskStateManager mgr = new TaskStateManager((ApplicationEventPublisher) e -> {});
        String taskId = "t1";
        mgr.initializeTask(taskId, TaskStatus.FAILED);
        TaskAggregate agg = new TaskAggregate(taskId, "p1", "tenant");
        agg.setRetryCount(3); agg.setMaxRetry(3); // 达到上限
        TaskRuntimeContext ctx = new TaskRuntimeContext("p1", taskId, "tenant", null);
        mgr.registerTaskAggregate(taskId, agg, ctx, 1);
        mgr.updateState(taskId, TaskStatus.RUNNING); // Guard 应阻止
        assertEquals(TaskStatus.FAILED, mgr.getState(taskId));
    }

    @Test
    void failedToRunningGuardAllowsWhenRetryBelowMax() {
        TaskStateManager mgr = new TaskStateManager((ApplicationEventPublisher) e -> {});
        String taskId = "t2";
        mgr.initializeTask(taskId, TaskStatus.FAILED);
        TaskAggregate agg = new TaskAggregate(taskId, "p1", "tenant");
        agg.setRetryCount(1); agg.setMaxRetry(3);
        TaskRuntimeContext ctx = new TaskRuntimeContext("p1", taskId, "tenant", null);
        mgr.registerTaskAggregate(taskId, agg, ctx, 1);
        mgr.updateState(taskId, TaskStatus.RUNNING);
        assertEquals(TaskStatus.RUNNING, mgr.getState(taskId));
    }

    @Test
    void runningToPausedRequiresFlag() {
        TaskStateManager mgr = new TaskStateManager((ApplicationEventPublisher) e -> {});
        String taskId = "t3";
        mgr.initializeTask(taskId, TaskStatus.RUNNING);
        TaskAggregate agg = new TaskAggregate(taskId, "p1", "tenant");
        TaskRuntimeContext ctx = new TaskRuntimeContext("p1", taskId, "tenant", null);
        mgr.registerTaskAggregate(taskId, agg, ctx, 2);
        // 未设置 pauseRequested
        mgr.updateState(taskId, TaskStatus.PAUSED);
        assertEquals(TaskStatus.RUNNING, mgr.getState(taskId));
        // 设置 pauseRequested
        ctx.requestPause();
        mgr.updateState(taskId, TaskStatus.PAUSED);
        assertEquals(TaskStatus.PAUSED, mgr.getState(taskId));
    }

    @Test
    void runningToCompletedOnlyAfterAllStages() {
        TaskStateManager mgr = new TaskStateManager((ApplicationEventPublisher) e -> {});
        String taskId = "t4";
        mgr.initializeTask(taskId, TaskStatus.RUNNING);
        TaskAggregate agg = new TaskAggregate(taskId, "p1", "tenant");
        agg.setCurrentStageIndex(0); // 未完成
        TaskRuntimeContext ctx = new TaskRuntimeContext("p1", taskId, "tenant", null);
        mgr.registerTaskAggregate(taskId, agg, ctx, 2);
        mgr.updateState(taskId, TaskStatus.COMPLETED);
        assertEquals(TaskStatus.RUNNING, mgr.getState(taskId));
        agg.setCurrentStageIndex(2); // 已完成 totalStages
        mgr.updateState(taskId, TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, mgr.getState(taskId));
    }
}
