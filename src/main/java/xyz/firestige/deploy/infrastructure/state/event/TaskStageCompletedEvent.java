package xyz.firestige.deploy.infrastructure.state.event;

import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.execution.StageResult;

/**
 * Stage 完成事件
 */
public class TaskStageCompletedEvent extends TaskStatusEvent {

    /**
     * Stage 名称
     */
    private String stageName;

    /**
     * Stage 执行结果
     */
    private StageResult stageResult;

    public TaskStageCompletedEvent() {
        super();
        setStatus(TaskStatus.RUNNING);
    }

    public TaskStageCompletedEvent(String taskId, String stageName, StageResult stageResult) {
        super(taskId, TaskStatus.RUNNING);
        this.stageName = stageName;
        this.stageResult = stageResult;
        setMessage("Stage 执行完成: " + stageName);
    }

    // Getters and Setters

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public StageResult getStageResult() {
        return stageResult;
    }

    public void setStageResult(StageResult stageResult) {
        this.stageResult = stageResult;
    }
}

