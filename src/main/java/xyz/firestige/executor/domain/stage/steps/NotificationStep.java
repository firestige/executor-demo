package xyz.firestige.executor.domain.stage.steps;

import xyz.firestige.executor.domain.stage.StageStep;
import xyz.firestige.executor.domain.task.TaskContext;

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
    public void execute(TaskContext ctx) throws Exception {
        // 未来：向外部服务发布新路由或配置；此处记录占位
    }

    @Override
    public void rollback(TaskContext ctx) throws Exception {
        // 未来：发布回滚动作；此处占位
    }
}

