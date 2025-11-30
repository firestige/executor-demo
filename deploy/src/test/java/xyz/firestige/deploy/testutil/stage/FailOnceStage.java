package xyz.firestige.deploy.testutil.stage;

import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.StageResult;
import xyz.firestige.deploy.infrastructure.execution.stage.StageStep;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 第一次失败，后续成功的测试Stage
 * <p>
 * 用途：
 * - 测试重试场景(fromCheckpoint)
 * - 验证Checkpoint机制是否正确恢复
 * - 测试失败→成功的状态转换
 *
 * @since T-023 测试体系重建
 */
public class FailOnceStage implements TaskStage {

    private final String name;
    private final AtomicInteger attemptCount;
    private final int failAtAttempt;

    public FailOnceStage(String name) {
        this(name, 1);
    }

    public FailOnceStage(String name, int failAtAttempt) {
        this.name = name;
        this.attemptCount = new AtomicInteger(0);
        this.failAtAttempt = failAtAttempt;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean canSkip(TaskRuntimeContext ctx) {
        return false;
    }

    @Override
    public StageResult execute(TaskRuntimeContext ctx) {
        int currentAttempt = attemptCount.incrementAndGet();

        if (currentAttempt == failAtAttempt) {
            FailureInfo failureInfo = FailureInfo.of(
                    ErrorType.SYSTEM_ERROR,
                    "Simulated failure on attempt " + currentAttempt,
                    name
            );
            StageResult result = StageResult.failure(name, failureInfo);
            result.setDuration(Duration.ZERO);
            return  result;
        }

        StageResult result = StageResult.success(name);
        result.setDuration(Duration.ZERO);
        return  result;
    }

    @Override
    public void rollback(TaskRuntimeContext ctx) {
        // 重置计数器，支持多次回滚测试
        attemptCount.set(0);
    }

    @Override
    public List<StageStep> getSteps() {
        return Collections.emptyList();
    }

    /**
     * 获取当前尝试次数（用于测试验证）
     */
    public int getAttemptCount() {
        return attemptCount.get();
    }

    /**
     * 重置尝试次数（用于测试场景复用）
     */
    public void reset() {
        attemptCount.set(0);
    }
}
