package xyz.firestige.deploy.infrastructure.state.event;

import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务失败事件
 */
public class TaskFailedEvent extends TaskStatusEvent {

    /**
     * 已完成的 Stage 列表
     */
    private List<String> completedStages;

    /**
     * 失败的 Stage
     */
    private String failedStage;

    public TaskFailedEvent() {
        super();
        setStatus(TaskStatus.FAILED);
        this.completedStages = new ArrayList<>();
    }

    public TaskFailedEvent(String taskId, FailureInfo failureInfo, List<String> completedStages, String failedStage) {
        super(taskId, TaskStatus.FAILED);
        setFailureInfo(failureInfo);
        this.completedStages = completedStages != null ? completedStages : new ArrayList<>();
        this.failedStage = failedStage;
        setMessage("任务执行失败，失败 Stage: " + failedStage + ", 已完成 Stage 数: " + this.completedStages.size());
    }

    // Getters and Setters

    public List<String> getCompletedStages() {
        return completedStages;
    }

    public void setCompletedStages(List<String> completedStages) {
        this.completedStages = completedStages;
    }

    public String getFailedStage() {
        return failedStage;
    }

    public void setFailedStage(String failedStage) {
        this.failedStage = failedStage;
    }
}

