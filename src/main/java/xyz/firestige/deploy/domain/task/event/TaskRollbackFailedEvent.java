package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务回滚失败事件
 */
public class TaskRollbackFailedEvent extends TaskStatusEvent {

    /**
     * 部分回滚成功的 Stage 列表
     */
    private List<String> partiallyRolledBackStages;

    public TaskRollbackFailedEvent() {
        super();
        setStatus(TaskStatus.ROLLBACK_FAILED);
        this.partiallyRolledBackStages = new ArrayList<>();
    }

    public TaskRollbackFailedEvent(String taskId, FailureInfo failureInfo, List<String> partiallyRolledBackStages) {
        super(taskId, TaskStatus.ROLLBACK_FAILED);
        setFailureInfo(failureInfo);
        this.partiallyRolledBackStages = partiallyRolledBackStages != null ? partiallyRolledBackStages : new ArrayList<>();
        setMessage("任务回滚失败，部分回滚成功 Stage 数: " + this.partiallyRolledBackStages.size());
    }

    // Getters and Setters

    public List<String> getPartiallyRolledBackStages() {
        return partiallyRolledBackStages;
    }

    public void setPartiallyRolledBackStages(List<String> partiallyRolledBackStages) {
        this.partiallyRolledBackStages = partiallyRolledBackStages;
    }
}

