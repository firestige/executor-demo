package xyz.firestige.deploy.state.strategy;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.state.TaskStatus;

/**
 * CREATED -> PENDING 转换策略（标记为待执行）
 *
 * @since Phase 18 - RF-13
 */
public class MarkAsPendingTransitionStrategy implements StateTransitionStrategy {

    @Override
    public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus) {
        return agg.getStatus() == TaskStatus.CREATED;
    }

    @Override
    public void execute(TaskAggregate agg, TaskRuntimeContext context, Object additionalData) {
        agg.markAsPending();
    }

    @Override
    public TaskStatus getFromStatus() {
        return TaskStatus.CREATED;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.PENDING;
    }
}
