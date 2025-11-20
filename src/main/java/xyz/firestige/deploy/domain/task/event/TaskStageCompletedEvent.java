package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.infrastructure.execution.StageResult;
import xyz.firestige.deploy.infrastructure.execution.StageStatus;

import java.time.Duration;

/**
 * Stage 完成事件
 */
public class TaskStageCompletedEvent extends TaskStageStatusEvent {

    /**
     * Stage 执行结果
     */
    private final StageResult stageResult;

    public TaskStageCompletedEvent(TaskInfo info, String stageName, StageResult stageResult) {
        super(info, stageName, StageStatus.COMPLETED);
        this.stageResult = stageResult;
        setMessage("Stage 执行完成: " + stageName);
    }

    public StageResult getStageResult() {
        return stageResult;
    }
}

