package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.event.WithFailureInfo;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskErrorView;
import xyz.firestige.deploy.domain.task.TaskInfoView;
import xyz.firestige.deploy.domain.task.TaskProgressView;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务回滚失败事件
 * <p>
 * T-036: 重构为使用三大视图
 */
public class TaskRollbackFailedEvent extends TaskStatusEvent implements WithFailureInfo {

    private final TaskInfoView taskInfo;
    private final TaskProgressView progress;
    private final TaskErrorView error;
    private final List<String> partiallyRolledBackStages;
    private final FailureInfo failureInfo;

    public TaskRollbackFailedEvent(TaskAggregate task, FailureInfo failureInfo, List<String> partiallyRolledBackStages) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.progress = task.getProgressView();
        this.error = TaskErrorView.from(failureInfo);
        this.failureInfo = failureInfo;
        this.partiallyRolledBackStages = partiallyRolledBackStages != null ? partiallyRolledBackStages : new ArrayList<>();
        setMessage("任务回滚失败，部分回滚成功 Stage 数: " + this.partiallyRolledBackStages.size());
    }

    public TaskInfoView getTaskInfoView() { return taskInfo; }
    public TaskProgressView getProgress() { return progress; }
    public TaskErrorView getError() { return error; }

    public List<String> getPartiallyRolledBackStages() {
        return partiallyRolledBackStages;
    }

    @Override
    public FailureInfo getFailureInfo() {
        return failureInfo;
    }
}

