package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;

/**
 * 任务暂停事件
 * <p>
 * T-036: 重构为使用三大视图
 * - TaskInfoView: 基本信息
 */
public class TaskPausedEvent extends TaskStatusEvent {

    // T-036: 使用视图
    private final TaskInfoView taskInfo;

    /**
     * 操作人
     */
    private final String pausedBy;

    /**
     * 暂停时的 Stage
     */
    private final String currentStage;

    // ============================================
    // 新构造器（T-036）
    // ============================================

    public TaskPausedEvent(TaskAggregate task, String pausedBy, String currentStage) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.pausedBy = pausedBy;
        this.currentStage = currentStage;
        setMessage("任务已暂停，当前 Stage: " + currentStage);
    }

    // ============================================
    // 旧构造器（向后兼容）
    // ============================================

    @Deprecated
    public TaskPausedEvent(TaskInfo info, String pausedBy, String currentStage) {
        super(info);
        this.taskInfo = getTaskInfo();
        this.pausedBy = pausedBy;
        this.currentStage = currentStage;
        setMessage("任务已暂停，当前 Stage: " + currentStage);
    }

    // ============================================
    // Getters
    // ============================================

    public String getPausedBy() {
        return pausedBy;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public TaskInfoView getTaskInfoView() {
        return taskInfo;
    }
}

