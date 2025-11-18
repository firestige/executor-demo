package xyz.firestige.deploy.infrastructure.state.strategy;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * FAILED/ROLLED_BACK -> RUNNING 转换策略（重试任务）
 *
 * @since Phase 18 - RF-13
 */
public class RetryTransitionStrategy implements StateTransitionStrategy {

    private final Integer globalMaxRetry;

    public RetryTransitionStrategy(Integer globalMaxRetry) {
        this.globalMaxRetry = globalMaxRetry;
    }

    @Override
    public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus) {
        // 只有 FAILED 或 ROLLED_BACK 可以重试
        TaskStatus currentStatus = agg.getStatus();
        if (currentStatus != TaskStatus.FAILED && currentStatus != TaskStatus.ROLLED_BACK) {
            return false;
        }
        
        // 检查重试次数
        if (agg.getRetryPolicy() != null) {
            return agg.getRetryPolicy().canRetry(globalMaxRetry);
        }
        
        // 兜底：使用旧的判断逻辑
        int retryCount = agg.getRetryCount();
        Integer maxRetry = agg.getMaxRetry();
        int effectiveMaxRetry = maxRetry != null ? maxRetry : (globalMaxRetry != null ? globalMaxRetry : Integer.MAX_VALUE);
        return retryCount < effectiveMaxRetry;
    }

    @Override
    public void execute(TaskAggregate agg, TaskRuntimeContext context, Object additionalData) {
        // additionalData: boolean fromCheckpoint
        boolean fromCheckpoint = additionalData instanceof Boolean ? (Boolean) additionalData : false;
        agg.retry(fromCheckpoint, globalMaxRetry);
    }

    @Override
    public TaskStatus getFromStatus() {
        // 支持两种源状态
        return TaskStatus.FAILED;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.RUNNING;
    }
}
