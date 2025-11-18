package xyz.firestige.executor.state.strategy;

import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.state.TaskStatus;

/**
 * PENDING -> RUNNING 转换策略（启动任务）
 *
 * @since Phase 18 - RF-13
 */
public class StartTransitionStrategy implements StateTransitionStrategy {

    @Override
    public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus) {
        return agg.getStatus() == TaskStatus.PENDING;
    }

    @Override
    public void execute(TaskAggregate agg, TaskRuntimeContext context, Object additionalData) {
        agg.start();  // 委托给聚合的业务方法
    }

    @Override
    public TaskStatus getFromStatus() {
        return TaskStatus.PENDING;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.RUNNING;
    }
}
