package xyz.firestige.deploy.infrastructure.state.strategy;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * PAUSED -> RUNNING 转换策略（恢复任务）
 *
 * @since Phase 18 - RF-13
 */
public class ResumeTransitionStrategy implements StateTransitionStrategy {

    @Override
    public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus) {
        // 必须是 PAUSED 状态，且没有暂停请求
        return agg.getStatus() == TaskStatus.PAUSED &&
               (context == null || !context.isPauseRequested());
    }

    @Override
    public void execute(TaskAggregate agg, TaskRuntimeContext context) {
        agg.resume();  // 委托给聚合
    }

    @Override
    public TaskStatus getFromStatus() {
        return TaskStatus.PAUSED;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.RUNNING;
    }
}
