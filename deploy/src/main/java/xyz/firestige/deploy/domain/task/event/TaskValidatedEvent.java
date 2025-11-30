package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;

/**
 * 任务校验通过事件
 * <p>
 * T-036: 重构为使用三大视图
 */
public class TaskValidatedEvent extends TaskStatusEvent {

    private final TaskInfoView taskInfo;
    private final int validatedCount;

    public TaskValidatedEvent(TaskAggregate task, int validatedCount) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.validatedCount = validatedCount;
        setMessage("任务校验通过，有效配置数量: " + validatedCount);
    }

    @Deprecated
    public TaskValidatedEvent(TaskInfo info, int validatedCount) {
        super(info);
        this.taskInfo = getTaskInfo();
        this.validatedCount = validatedCount;
        setMessage("任务校验通过，有效配置数量: " + validatedCount);
    }

    public int getValidatedCount() { return validatedCount; }
    public TaskInfoView getTaskInfoView() { return taskInfo; }
}

