package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务回滚中事件
 */
public class TaskRollingBackEvent extends TaskStatusEvent {

    /**
     * 回滚原因
     */
    private String reason;

    /**
     * 需要回滚的 Stage 列表
     */
    private List<String> stagesToRollback;

    public TaskRollingBackEvent() {
        super();
        setStatus(TaskStatus.ROLLING_BACK);
        this.stagesToRollback = new ArrayList<>();
    }

    public TaskRollingBackEvent(String taskId, String reason, List<String> stagesToRollback) {
        super(taskId, TaskStatus.ROLLING_BACK);
        this.reason = reason;
        this.stagesToRollback = stagesToRollback != null ? stagesToRollback : new ArrayList<>();
        setMessage("任务开始回滚，原因: " + reason + ", Stage 数: " + this.stagesToRollback.size());
    }

    // Getters and Setters

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getStagesToRollback() {
        return stagesToRollback;
    }

    public void setStagesToRollback(List<String> stagesToRollback) {
        this.stagesToRollback = stagesToRollback;
    }
}

