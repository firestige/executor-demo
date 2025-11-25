package xyz.firestige.deploy.infrastructure.state.strategy;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * RUNNING -> COMPLETED 转换策略（完成任务）
 * <p>
 * 注意：正常情况下由 completeStage() 自动触发，不需要外部调用
 *
 * @since Phase 18 - RF-13
 */
public class CompleteTransitionStrategy implements StateTransitionStrategy {

    private final Integer totalStages;

    public CompleteTransitionStrategy(Integer totalStages) {
        this.totalStages = totalStages;
    }

    @Override
    public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus) {
        // 必须是 RUNNING 状态，且所有 Stage 已完成
        if (agg.getStatus() != TaskStatus.RUNNING) {
            return false;
        }
        
        // 检查 Stage 进度
        if (agg.getStageProgress() != null) {
            return agg.getStageProgress().isCompleted();
        }
        
        // 兜底：使用 totalStages
        if (totalStages != null) {
            return agg.getCurrentStageIndex() >= totalStages;
        }
        
        return false;
    }

    @Override
    public void execute(TaskAggregate agg, TaskRuntimeContext context) {
        // RF-13: complete() 是 private 方法，由 completeStage() 自动调用
        // 这里不需要做任何事，状态已经被 completeStage() 修改
        // 如果外部直接调用 updateState(COMPLETED)，这里也不会执行
    }

    @Override
    public TaskStatus getFromStatus() {
        return TaskStatus.RUNNING;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.COMPLETED;
    }
}
