package xyz.firestige.deploy.testutil.stage;

import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.StageResult;
import xyz.firestige.deploy.infrastructure.execution.stage.StageStep;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 永远成功的测试Stage
 * <p>
 * 用途：
 * - 测试正常执行流程
 * - 多阶段串联测试
 * - 作为占位Stage快速构建测试场景
 *
 * @since T-023 测试体系重建
 */
public class AlwaysSuccessStage implements TaskStage {

    private final String name;
    private final Duration executionTime;

    public AlwaysSuccessStage(String name) {
        this(name, Duration.ZERO);
    }

    public AlwaysSuccessStage(String name, Duration executionTime) {
        this.name = name;
        this.executionTime = executionTime;
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
        if (!executionTime.isZero()) {
            try {
                Thread.sleep(executionTime.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return StageResult.success(name);
    }

    @Override
    public void rollback(TaskRuntimeContext ctx) {
        // 成功Stage无需回滚逻辑
    }

    @Override
    public List<StageStep> getSteps() {
        return Collections.emptyList();
    }
}
