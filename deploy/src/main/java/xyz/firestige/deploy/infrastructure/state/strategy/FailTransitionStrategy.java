package xyz.firestige.deploy.infrastructure.state.strategy;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.execution.StageResult;

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
    public void execute(TaskAggregate agg, TaskRuntimeContext context) {
        // additionalData: StageResult 或 FailureInfo
        // 从上下文中获取失败信息
        Object additionalData = context.getAdditionalData("failureData");
        if (additionalData instanceof StageResult result) {
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
