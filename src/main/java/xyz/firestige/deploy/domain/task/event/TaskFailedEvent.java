package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.event.WithFailureInfo;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务失败事件
 */
public class TaskFailedEvent extends TaskStatusEvent implements WithFailureInfo {

    /**
     * 已完成的 Stage 列表
     */
    private final List<String> completedStages;

    /**
     * 失败的 Stage
     */
    private final String failedStage;

    private final FailureInfo failureInfo;

    public TaskFailedEvent(TaskInfo info, FailureInfo failureInfo, List<String> completedStages, String failedStage) {
        super(info);
        this.failedStage = failedStage;
        this.completedStages = completedStages != null ? completedStages : new ArrayList<>();
        this.failureInfo = failureInfo;
        setMessage("任务执行失败，失败 Stage: " + failedStage + ", 已完成 Stage 数: " + this.completedStages.size());
    }

    // Getters and Setters

    public List<String> getCompletedStages() {
        return completedStages;
    }

    public String getFailedStage() {
        return failedStage;
    }

    @Override
    public FailureInfo getFailureInfo() {
        return failureInfo;
    }
}