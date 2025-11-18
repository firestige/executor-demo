package xyz.firestige.deploy.state.strategy;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.state.TaskStatus;

/**
 * 任意状态 -> ROLLING_BACK 转换策略（开始回滚）
 *
 * @since Phase 18 - RF-13
 */
public class RollbackTransitionStrategy implements StateTransitionStrategy {

    @Override
    public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus) {
        // 必须有回滚快照
        return agg.getPrevConfigSnapshot() != null;
    }

    @Override
    public void execute(TaskAggregate agg, TaskRuntimeContext context, Object additionalData) {
        // additionalData: String reason
        String reason = additionalData instanceof String ? (String) additionalData : "Unknown reason";
        agg.startRollback(reason);
    }

    @Override
    public TaskStatus getFromStatus() {
        // 任意状态都可以开始回滚（只要有快照）
        return null;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.ROLLING_BACK;
    }
}
