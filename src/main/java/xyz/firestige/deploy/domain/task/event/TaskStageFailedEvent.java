package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.infrastructure.execution.StageStatus;

/**
 * Stage 失败事件
 */
public class TaskStageFailedEvent extends TaskStageStatusEvent {

    private final FailureInfo failureInfo;

    public TaskStageFailedEvent(TaskInfo info, String stageName, FailureInfo failureInfo) {
        super(info, stageName, StageStatus.FAILED);
        this.failureInfo = failureInfo;
        setMessage("Stage 执行失败: " + stageName);
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }
}

