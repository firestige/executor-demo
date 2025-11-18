package xyz.firestige.deploy.state.strategy;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.state.TaskStatus;

/**
 * 任意状态 -> CANCELLED 转换策略（取消任务）
 *
 * @since Phase 18 - RF-13
 */
public class CancelTransitionStrategy implements StateTransitionStrategy {

    @Override
    public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus) {
        // 不能取消终态任务
        return !agg.getStatus().isTerminal();
    }

    @Override
    public void execute(TaskAggregate agg, TaskRuntimeContext context, Object additionalData) {
        // additionalData: String cancelledBy
        String cancelledBy = additionalData instanceof String ? (String) additionalData : "System";
        agg.cancel(cancelledBy);
    }

    @Override
    public TaskStatus getFromStatus() {
        // 任意非终态都可以取消
        return null;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.CANCELLED;
    }
}
