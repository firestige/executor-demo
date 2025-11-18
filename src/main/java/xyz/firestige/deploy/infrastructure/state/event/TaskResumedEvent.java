package xyz.firestige.deploy.infrastructure.state.event;

import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * 任务恢复事件
 */
public class TaskResumedEvent extends TaskStatusEvent {

    /**
     * 操作人
     */
    private String resumedBy;

    /**
     * 恢复的起始 Stage
     */
    private String resumeFromStage;

    public TaskResumedEvent() {
        super();
        setStatus(TaskStatus.RESUMING);
    }

    public TaskResumedEvent(String taskId, String resumedBy, String resumeFromStage) {
        super(taskId, TaskStatus.RESUMING);
        this.resumedBy = resumedBy;
        this.resumeFromStage = resumeFromStage;
        setMessage("任务已恢复，从 Stage 继续: " + resumeFromStage);
    }

    // Getters and Setters

    public String getResumedBy() {
        return resumedBy;
    }

    public void setResumedBy(String resumedBy) {
        this.resumedBy = resumedBy;
    }

    public String getResumeFromStage() {
        return resumeFromStage;
    }

    public void setResumeFromStage(String resumeFromStage) {
        this.resumeFromStage = resumeFromStage;
    }
}

