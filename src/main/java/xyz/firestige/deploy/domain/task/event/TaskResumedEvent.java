package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

/**
 * 任务恢复事件
 */
public class TaskResumedEvent extends TaskStatusEvent {

    /**
     * 操作人
     */
    private final String resumedBy;

    /**
     * 恢复的起始 Stage
     */
    private final String resumeFromStage;

    public TaskResumedEvent(TaskInfo info, String resumedBy, String resumeFromStage) {
        super(info);
        this.resumedBy = resumedBy;
        this.resumeFromStage = resumeFromStage;
        setMessage("任务已恢复，从 Stage 继续: " + resumeFromStage);
    }

    // Getters and Setters

    public String getResumedBy() {
        return resumedBy;
    }

    public String getResumeFromStage() {
        return resumeFromStage;
    }
}

