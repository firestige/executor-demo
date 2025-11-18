package xyz.firestige.deploy.domain.stage.steps;

import xyz.firestige.deploy.domain.stage.StageStep;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;

/**
 * 通知步骤占位：未来对接实际 ServiceNotification 行为。
 */
public class NotificationStep implements StageStep {
    private final String stepName;

    public NotificationStep(String stepName) {
        this.stepName = stepName;
    }

    @Override
    public String getStepName() {
        return stepName;
    }

    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 占位通知逻辑
    }
}
