package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;
import xyz.firestige.deploy.domain.task.TaskProgressView;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务回滚完成事件
 * <p>
 * T-036: 重构为使用三大视图
 * - TaskInfoView: 基本信息
 * - TaskProgressView: 进度信息
 */
public class TaskRolledBackEvent extends TaskStatusEvent {

    // T-036: 使用视图
    private final TaskInfoView taskInfo;
    private final TaskProgressView progress;

    /**
     * 已回滚的 Stage 列表
     */
    private final List<String> rolledBackStages;

    // ============================================
    // 新构造器（T-036）
    // ============================================

    /**
     * 新构造器：接受 TaskAggregate
     *
     * @param task Task 聚合根
     * @param rolledBackStages 已回滚的 Stage 列表
     * @since T-036
     */
    public TaskRolledBackEvent(TaskAggregate task, List<String> rolledBackStages) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.progress = task.getProgressView();
        this.rolledBackStages = rolledBackStages != null ? rolledBackStages : new ArrayList<>();
        setMessage("任务回滚完成，回滚 Stage 数: " + this.rolledBackStages.size());
    }

    // ============================================
    // 旧构造器（向后兼容）
    // ============================================

    /**
     * 旧构造器：接受 TaskInfo
     *
     * @deprecated 使用 TaskRolledBackEvent(TaskAggregate, List) 代替
     */
    @Deprecated
    public TaskRolledBackEvent(TaskInfo info, List<String> rolledBackStages) {
        super(info);
        this.taskInfo = getTaskInfo();
        this.progress = null;
        this.rolledBackStages = rolledBackStages != null ? rolledBackStages : new ArrayList<>();
        setMessage("任务回滚完成，回滚 Stage 数: " + this.rolledBackStages.size());
    }

    // ============================================
    // Getters
    // ============================================

    public List<String> getRolledBackStages() {
        return rolledBackStages;
    }

    public TaskInfoView getTaskInfoView() {
        return taskInfo;
    }

    public TaskProgressView getProgress() {
        return progress;
    }
}
