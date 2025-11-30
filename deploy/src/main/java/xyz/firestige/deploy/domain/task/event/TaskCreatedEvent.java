package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;

import java.util.Collections;
import java.util.List;

/**
 * Task 创建事件
 * <p>
 * T-036: 重构为使用三大视图
 */
public class TaskCreatedEvent extends TaskStatusEvent {

    private final TaskInfoView taskInfo;
    private final List<String> stageNames;

    public TaskCreatedEvent(TaskAggregate task, List<String> stageNames) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.stageNames = stageNames != null ? stageNames : Collections.emptyList();
        setMessage("任务已创建，Stage 数: " + this.stageNames.size());
    }

    public List<String> getStageNames() {
        return Collections.unmodifiableList(stageNames);
    }

    @Override
    public String toString() {
        return "TaskCreatedEvent{" +
                "taskId=" + getTaskId() +
                ", tenantId=" + getTenantId() +
                ", planId=" + getPlanId() +
                ", stageCount=" + stageNames.size() +
                '}';
    }
}
