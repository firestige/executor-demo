package xyz.firestige.executor.state.strategy;

import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.state.TaskStatus;

/**
 * RUNNING -> PAUSED 转换策略（暂停任务）
 *
 * @since Phase 18 - RF-13
 */
public class PauseTransitionStrategy implements StateTransitionStrategy {

    @Override
    public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus) {
        // 必须是 RUNNING 状态，且有暂停请求
        return agg.getStatus() == TaskStatus.RUNNING && 
               context != null && 
               context.isPauseRequested();
    }

    @Override
    public void execute(TaskAggregate agg, TaskRuntimeContext context, Object additionalData) {
        agg.applyPauseAtStageBoundary();  // 委托给聚合
    }

    @Override
    public TaskStatus getFromStatus() {
        return TaskStatus.RUNNING;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.PAUSED;
    }
}
