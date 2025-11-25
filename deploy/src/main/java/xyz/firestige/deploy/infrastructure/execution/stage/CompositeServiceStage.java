package xyz.firestige.deploy.infrastructure.execution.stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.StageResult;
import xyz.firestige.deploy.infrastructure.execution.stage.rollback.RollbackStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 组合服务 Stage：按顺序执行多个 Step，任一步失败则失败。
 */
public class CompositeServiceStage implements TaskStage {

    private static final Logger log = LoggerFactory.getLogger(CompositeServiceStage.class);

    private final String name;
    private final List<StageStep> steps = new ArrayList<>();
    private final RollbackStrategy rollbackStrategy; // 新增回滚策略

    public CompositeServiceStage(String name, List<StageStep> steps) {
        this(name, steps, null);
    }
    public CompositeServiceStage(String name, List<StageStep> steps, RollbackStrategy rollbackStrategy) {
        this.name = Objects.requireNonNull(name);
        if (steps != null) this.steps.addAll(steps);
        this.rollbackStrategy = rollbackStrategy;
    }

    @Override
    public String getName() { return name; }

    @Override
    public boolean canSkip(TaskRuntimeContext ctx) { return false; }

    @Override
    public StageResult execute(TaskRuntimeContext ctx) {
        StageResult result = StageResult.start(name);
        for (StageStep step : steps) {
            var stepRes = StepResult.start(step.getStepName());
            try {
                ctx.injectMdc(step.getStepName());
                step.execute(ctx);
                stepRes.finishSuccess();
                result.addStepResult(stepRes);
            } catch (Exception ex) {
                log.error("Stage step failed: stage={}, step={}, err={}", name, step.getStepName(), ex.getMessage(), ex);
                FailureInfo failureInfo = FailureInfo.fromException(ex, ErrorType.SYSTEM_ERROR, name);
                stepRes.finishFailure(ex.getMessage());
                result.addStepResult(stepRes);
                result.failure(failureInfo);
                return result;
            }
        }
        result.success();
        return result;
    }

    @Override
    public void rollback(TaskRuntimeContext ctx) {
        // Stage 不直接执行策略（由 TaskExecutor 驱动），这里只做占位日志。
        log.info("Stage rollback placeholder (strategy invoked by executor if present): stage={}", name);
    }

    public RollbackStrategy getRollbackStrategy() { return rollbackStrategy; }

    @Override
    public List<StageStep> getSteps() { return new ArrayList<>(steps); }
}
