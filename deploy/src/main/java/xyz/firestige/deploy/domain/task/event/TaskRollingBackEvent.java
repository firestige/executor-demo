package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;
import xyz.firestige.deploy.domain.task.TaskProgressView;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务回滚中事件
 * <p>
 * T-036: 重构为使用三大视图
 * - TaskInfoView: 基本信息
 * - TaskProgressView: 进度信息
 */
public class TaskRollingBackEvent extends TaskStatusEvent {

    // T-036: 使用视图
    private final TaskInfoView taskInfo;
    private final TaskProgressView progress;

    /**
     * 回滚原因
     */
    private final String reason;

    /**
     * 需要回滚的 Stage 列表
     */
    private final List<String> stagesToRollback;

    // ============================================
    // 新构造器（T-036）
    // ============================================

    /**
     * 新构造器：接受 TaskAggregate
     *
     * @param task Task 聚合根
     * @param reason 回滚原因
     * @param stagesToRollback 需要回滚的 Stage 列表
     * @since T-036
     */
    public TaskRollingBackEvent(TaskAggregate task, String reason, List<String> stagesToRollback) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.progress = task.getProgressView();
        this.reason = reason;
        this.stagesToRollback = stagesToRollback != null ? stagesToRollback : new ArrayList<>();
        setMessage("任务开始回滚，原因: " + reason + ", Stage 数: " + this.stagesToRollback.size());
    }

    // ============================================
    // 旧构造器（向后兼容）
    // ============================================

    /**
     * 旧构造器：接受 TaskInfo
     *
     * @deprecated 使用 TaskRollingBackEvent(TaskAggregate, String, List) 代替
     */
    @Deprecated
    public TaskRollingBackEvent(TaskInfo info, String reason, List<String> stagesToRollback) {
        super(info);
        this.taskInfo = getTaskInfo();
        this.progress = null;
        this.reason = reason;
        this.stagesToRollback = stagesToRollback != null ? stagesToRollback : new ArrayList<>();
        setMessage("任务开始回滚，原因: " + reason + ", Stage 数: " + this.stagesToRollback.size());
    }

    // ============================================
    // Getters
    // ============================================

    public String getReason() {
        return reason;
    }

    public List<String> getStagesToRollback() {
        return stagesToRollback;
    }

    public TaskInfoView getTaskInfoView() {
        return taskInfo;
    }

    public TaskProgressView getProgress() {
        return progress;
    }
}

