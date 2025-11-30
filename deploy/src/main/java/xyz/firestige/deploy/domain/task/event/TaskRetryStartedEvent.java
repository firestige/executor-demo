package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskErrorView;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;
import xyz.firestige.deploy.domain.task.TaskProgressView;

/**
 * 任务重试开始事件
 * <p>
 * 携带信息：
 * <ul>
 *   <li>是否从检查点恢复</li>
 *   <li>当前进度（仅在从检查点恢复时有效）</li>
 *   <li>上次错误信息（可选）</li>
 * </ul>
 * <p>
 * T-036: 重构为使用三大视图
 * - TaskInfoView: 基本信息
 * - TaskProgressView: 进度信息
 * - TaskErrorView: 上次错误信息（可选）
 */
public class TaskRetryStartedEvent extends TaskStatusEvent {

    // T-036: 使用视图
    private final TaskInfoView taskInfo;
    private final TaskProgressView progress;
    private final TaskErrorView lastError;

    private final boolean fromCheckpoint;

    // ============================================
    // 新构造器（T-036）
    // ============================================

    /**
     * 新构造器：接受 TaskAggregate
     *
     * @param task Task 聚合根
     * @param fromCheckpoint 是否从检查点恢复
     * @since T-036
     */
    public TaskRetryStartedEvent(TaskAggregate task, boolean fromCheckpoint) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.progress = task.getProgressView();
        this.lastError = task.getFailureInfo() != null ? TaskErrorView.from(task.getFailureInfo()) : null;
        this.fromCheckpoint = fromCheckpoint;
        setMessage(fromCheckpoint ? "从检查点重试任务" : "从头重试任务");
    }

    // ============================================
    // 旧构造器（向后兼容）
    // ============================================

    /**
     * 旧构造器：接受 TaskInfo
     *
     * @deprecated 使用 TaskRetryStartedEvent(TaskAggregate, boolean) 代替
     */
    @Deprecated
    public TaskRetryStartedEvent(TaskInfo info, boolean fromCheckpoint) {
        super(info);
        this.taskInfo = getTaskInfo();
        this.progress = null;
        this.lastError = null;
        this.fromCheckpoint = fromCheckpoint;
        setMessage(fromCheckpoint ? "从检查点重试任务" : "从头重试任务");
    }

    // ============================================
    // Getters
    // ============================================

    public boolean isFromCheckpoint() {
        return fromCheckpoint; 
    }

    public TaskInfoView getTaskInfoView() {
        return taskInfo;
    }

    public TaskProgressView getProgress() {
        return progress;
    }

    public TaskErrorView getLastError() {
        return lastError;
    }
}
