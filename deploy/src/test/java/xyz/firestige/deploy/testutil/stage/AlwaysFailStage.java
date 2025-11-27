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

/**
 * 永远失败的测试Stage
 * <p>
 * 用途：
 * - 测试失败处理流程
 * - 测试Checkpoint保存
 * - 测试失败后的状态转换
 *
 * @since T-023 测试体系重建
 */
public class AlwaysFailStage implements TaskStage {

    private final String name;
    private final ErrorType errorType;
    private final String errorMessage;

    public AlwaysFailStage(String name) {
        this(name, ErrorType.SYSTEM_ERROR, "Simulated stage failure");
    }

    public AlwaysFailStage(String name, ErrorType errorType, String errorMessage) {
        this.name = name;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
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
        FailureInfo failureInfo = FailureInfo.of(errorType, errorMessage, name);
        return StageResult.failure(name, failureInfo);
    }

    @Override
    public void rollback(TaskRuntimeContext ctx) {
        // 失败的Stage在回滚时可能需要清理
    }

    @Override
    public List<StageStep> getSteps() {
        return Collections.emptyList();
    }
}
