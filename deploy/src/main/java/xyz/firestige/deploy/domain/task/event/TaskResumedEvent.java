package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;

/**
 * 任务恢复事件
 * <p>
 * T-036: 重构为使用三大视图
 */
public class TaskResumedEvent extends TaskStatusEvent {

    private final TaskInfoView taskInfo;
    private final String resumedBy;
    private final String resumeFromStage;

    public TaskResumedEvent(TaskAggregate task, String resumedBy, String resumeFromStage) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.resumedBy = resumedBy;
        this.resumeFromStage = resumeFromStage;
        setMessage("任务已恢复，从 Stage 继续: " + resumeFromStage);
    }

    @Deprecated
    public TaskResumedEvent(TaskInfo info, String resumedBy, String resumeFromStage) {
        super(info);
        this.taskInfo = getTaskInfo();
        this.resumedBy = resumedBy;
        this.resumeFromStage = resumeFromStage;
        setMessage("任务已恢复，从 Stage 继续: " + resumeFromStage);
    }

    public String getResumedBy() {
        return resumedBy;
    }

    public String getResumeFromStage() {
        return resumeFromStage;
    }

    public TaskInfoView getTaskInfoView() {
        return taskInfo;
    }
}

