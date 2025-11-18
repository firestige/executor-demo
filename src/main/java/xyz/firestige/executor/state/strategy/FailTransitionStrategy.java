package xyz.firestige.executor.state.strategy;

import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.StageResult;
import xyz.firestige.executor.state.TaskStatus;

/**
 * RUNNING -> FAILED 转换策略（任务失败）
 *
 * @since Phase 18 - RF-13
 */
public class FailTransitionStrategy implements StateTransitionStrategy {

    @Override
    public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus) {
        return agg.getStatus() == TaskStatus.RUNNING;
    }

    @Override
    public void execute(TaskAggregate agg, TaskRuntimeContext context, Object additionalData) {
        // additionalData: StageResult 或 FailureInfo
        if (additionalData instanceof StageResult) {
            StageResult result = (StageResult) additionalData;
            agg.failStage(result);
        } else {
            // 兼容：如果没有 StageResult，创建一个默认的
            FailureInfo failureInfo = additionalData instanceof FailureInfo 
                ? (FailureInfo) additionalData
                : new FailureInfo("UNKNOWN_ERROR", "Unknown error", null);
            
            String stageName = "Stage-" + agg.getCurrentStageIndex();
            StageResult result = StageResult.failure(stageName, failureInfo);
            agg.failStage(result);
        }
    }

    @Override
    public TaskStatus getFromStatus() {
        return TaskStatus.RUNNING;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.FAILED;
    }
}
