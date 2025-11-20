package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务回滚中事件
 */
public class TaskRollingBackEvent extends TaskStatusEvent {

    /**
     * 回滚原因
     */
    private final String reason;

    /**
     * 需要回滚的 Stage 列表
     */
    private final List<String> stagesToRollback;

    public TaskRollingBackEvent(TaskInfo info, String reason, List<String> stagesToRollback) {
        super(info);
        this.reason = reason;
        this.stagesToRollback = stagesToRollback != null ? stagesToRollback : new ArrayList<>();
        setMessage("任务开始回滚，原因: " + reason + ", Stage 数: " + this.stagesToRollback.size());
    }

    // Getters and Setters

    public String getReason() {
        return reason;
    }

    public List<String> getStagesToRollback() {
        return stagesToRollback;
    }
}

