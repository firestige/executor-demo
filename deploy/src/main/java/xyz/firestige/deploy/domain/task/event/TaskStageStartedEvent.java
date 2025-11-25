package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.infrastructure.execution.StageStatus;

public class TaskStageStartedEvent extends TaskStageStatusEvent {
    /**
     * 总步骤数
     */
    private final int totalSteps;

    public TaskStageStartedEvent(TaskInfo info, String stageName, int totalStages) {
        super(info, stageName, StageStatus.RUNNING);
        this.totalSteps = totalStages;
        setMessage("Stage开始执行，总 Step 数: " + totalStages);
    }

    public int getTotalStages() {
        return totalSteps;
    }
}
