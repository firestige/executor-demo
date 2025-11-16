package xyz.firestige.executor.domain.stage.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.executor.domain.stage.StageStep;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;

/**
 * 广播步骤：模拟对外广播配置更新事件。
 */
public class BroadcastStep implements StageStep {
    private static final Logger log = LoggerFactory.getLogger(BroadcastStep.class);
    private final String stepName;

    public BroadcastStep(String stepName) { this.stepName = stepName; }
    @Override
    public String getStepName() { return stepName; }
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        log.info("[BroadcastStep] 广播配置更新 step={} taskId={}", stepName, ctx != null ? ctx.getTaskId() : "null");
    }
}
