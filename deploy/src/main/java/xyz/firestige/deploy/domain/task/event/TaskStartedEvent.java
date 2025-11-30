package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;
import xyz.firestige.deploy.domain.task.TaskProgressView;

/**
 * 任务开始执行事件
 * <p>
 * T-036: 重构为使用三大视图
 * - TaskInfoView: 基本信息
 * - TaskProgressView: 进度信息
 */
public class TaskStartedEvent extends TaskStatusEvent {

    // T-036: 使用视图
    private final TaskInfoView taskInfo;
    private final TaskProgressView progress;

    /**
     * 总的 Stage 数量
     */
    private final int totalStages;

    // ============================================
    // 新构造器（T-036）
    // ============================================

    /**
     * 新构造器：接受 TaskAggregate
     *
     * @param task Task 聚合根
     * @param totalStages 总 Stage 数量
     * @since T-036
     */
    public TaskStartedEvent(TaskAggregate task, int totalStages) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.progress = task.getProgressView();
        this.totalStages = totalStages;
        setMessage("任务开始执行，总 Stage 数: " + totalStages);
    }

    // ============================================
    // 旧构造器（向后兼容）
    // ============================================

    /**
     * 旧构造器：接受 TaskInfo
     *
     * @deprecated 使用 TaskStartedEvent(TaskAggregate, int) 代替
     */
    @Deprecated
    public TaskStartedEvent(TaskInfo info, int totalStages) {
        super(info);
        this.taskInfo = getTaskInfo();
        this.progress = null;
        this.totalStages = totalStages;
        setMessage("任务开始执行，总 Stage 数: " + totalStages);
    }

    // ============================================
    // Getters
    // ============================================

    public int getTotalStages() {
        return totalStages;
    }

    public TaskInfoView getTaskInfoView() {
        return taskInfo;
    }

    public TaskProgressView getProgress() {
        return progress;
    }
}

