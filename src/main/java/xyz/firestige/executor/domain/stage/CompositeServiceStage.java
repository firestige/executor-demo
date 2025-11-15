package xyz.firestige.executor.domain.stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.executor.domain.task.TaskContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 组合服务 Stage：按顺序执行多个 Step，任一步失败则失败。
 */
public class CompositeServiceStage implements TaskStage {

    private static final Logger log = LoggerFactory.getLogger(CompositeServiceStage.class);

    private final String name;
    private final List<StageStep> steps = new ArrayList<>();

    public CompositeServiceStage(String name, List<StageStep> steps) {
        this.name = Objects.requireNonNull(name);
        if (steps != null) this.steps.addAll(steps);
    }

    @Override
    public String getName() { return name; }

    @Override
    public boolean canSkip(TaskContext ctx) { return false; }

    @Override
    public StageExecutionResult execute(TaskContext ctx) {
        StageExecutionResult result = StageExecutionResult.start(name);
        for (StageStep step : steps) {
            var stepRes = StepExecutionResult.start(step.getStepName());
            try {
                ctx.injectMdc(step.getStepName());
                step.execute(ctx);
                stepRes.finishSuccess();
                result.addStepResult(stepRes);
            } catch (Exception ex) {
                log.error("Stage step failed: stage={}, step={}, err={}", name, step.getStepName(), ex.getMessage(), ex);
                stepRes.finishFailure(ex.getMessage());
                result.addStepResult(stepRes);
                result.finishFailure(ex.getMessage());
                return result;
            }
        }
        result.finishSuccess();
        return result;
    }

    @Override
    public void rollback(TaskContext ctx) {
        // 逆序回滚
        List<StageStep> copy = new ArrayList<>(steps);
        Collections.reverse(copy);
        for (StageStep step : copy) {
            try {
                step.rollback(ctx);
            } catch (Exception ex) {
                log.warn("Rollback step failed: stage={}, step={}, err={}", name, step.getStepName(), ex.getMessage(), ex);
            }
        }
    }

    @Override
    public List<StageStep> getSteps() { return new ArrayList<>(steps); }
}

