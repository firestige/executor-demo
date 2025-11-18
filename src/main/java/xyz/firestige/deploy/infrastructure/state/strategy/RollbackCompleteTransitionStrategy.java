package xyz.firestige.deploy.infrastructure.state.strategy;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * ROLLING_BACK -> ROLLED_BACK 转换策略（回滚完成）
 *
 * @since Phase 18 - RF-13
 */
public class RollbackCompleteTransitionStrategy implements StateTransitionStrategy {

    @Override
    public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus) {
        return agg.getStatus() == TaskStatus.ROLLING_BACK;
    }

    @Override
    public void execute(TaskAggregate agg, TaskRuntimeContext context) {
        agg.completeRollback();
    }

    @Override
    public TaskStatus getFromStatus() {
        return TaskStatus.ROLLING_BACK;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.ROLLED_BACK;
    }
}
