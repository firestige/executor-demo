package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.infrastructure.execution.StageStatus;

public abstract class TaskStageStatusEvent extends TaskStatusEvent {
    /**
     * Stage 名称
     */
    private final String stageName;

    /**
     * Stage 状态
     */
    private final StageStatus stageStatus;

    public TaskStageStatusEvent(TaskInfo info, String stageName, StageStatus stageStatus) {
        super(info);
        this.stageName = stageName;
        this.stageStatus = stageStatus;
    }

    public String getStageName() {
        return stageName;
    }

    public  StageStatus getStageStatus() {
        return stageStatus;
    }
}
