package xyz.firestige.executor.state.strategy;

import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.state.TaskStatus;

/**
 * ROLLING_BACK -> ROLLBACK_FAILED 转换策略（回滚失败）
 *
 * @since Phase 18 - RF-13
 */
public class RollbackFailTransitionStrategy implements StateTransitionStrategy {

    @Override
    public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus) {
        return agg.getStatus() == TaskStatus.ROLLING_BACK;
    }

    @Override
    public void execute(TaskAggregate agg, TaskRuntimeContext context, Object additionalData) {
        // additionalData: String reason
        String reason = additionalData instanceof String ? (String) additionalData : "Unknown failure";
        agg.failRollback(reason);
    }

    @Override
    public TaskStatus getFromStatus() {
        return TaskStatus.ROLLING_BACK;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.ROLLBACK_FAILED;
    }
}
