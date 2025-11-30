package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.event.WithFailureInfo;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskErrorView;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;
import xyz.firestige.deploy.domain.task.TaskProgressView;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务失败事件
 * <p>
 * T-036: 重构为使用三大视图
 * - TaskInfoView: 基本信息
 * - TaskProgressView: 进度信息
 * - TaskErrorView: 错误信息
 */
public class TaskFailedEvent extends TaskStatusEvent implements WithFailureInfo {

    // T-036: 使用视图
    private final TaskInfoView taskInfo;
    private final TaskProgressView progress;
    private final TaskErrorView error;

    /**
     * 已完成的 Stage 列表
     */
    private final List<String> completedStages;

    /**
     * 失败的 Stage
     */
    private final String failedStage;

    private final FailureInfo failureInfo;

    // ============================================
    // 新构造器（T-036）
    // ============================================

    /**
     * 新构造器：接受 TaskAggregate
     *
     * @param task Task 聚合根
     * @param failureInfo 失败信息
     * @param completedStages 已完成的 Stage 列表
     * @param failedStage 失败的 Stage
     * @since T-036
     */
    public TaskFailedEvent(TaskAggregate task, FailureInfo failureInfo, List<String> completedStages, String failedStage) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.progress = task.getProgressView();
        this.error = TaskErrorView.from(failureInfo);
        this.failedStage = failedStage;
        this.completedStages = completedStages != null ? completedStages : new ArrayList<>();
        this.failureInfo = failureInfo;
        setMessage("任务执行失败，失败 Stage: " + failedStage + ", 已完成 Stage 数: " + this.completedStages.size());
    }

    // ============================================
    // 旧构造器（向后兼容）
    // ============================================

    /**
     * 旧构造器：接受 TaskInfo
     *
     * @deprecated 使用 TaskFailedEvent(TaskAggregate, FailureInfo, List, String) 代替
     */
    @Deprecated
    public TaskFailedEvent(TaskInfo info, FailureInfo failureInfo, List<String> completedStages, String failedStage) {
        super(info);
        this.taskInfo = getTaskInfo();
        this.progress = null;
        this.error = TaskErrorView.from(failureInfo);
        this.failedStage = failedStage;
        this.completedStages = completedStages != null ? completedStages : new ArrayList<>();
        this.failureInfo = failureInfo;
        setMessage("任务执行失败，失败 Stage: " + failedStage + ", 已完成 Stage 数: " + this.completedStages.size());
    }

    // ============================================
    // Getters
    // ============================================

    public List<String> getCompletedStages() {
        return completedStages;
    }

    public String getFailedStage() {
        return failedStage;
    }

    @Override
    public FailureInfo getFailureInfo() {
        return failureInfo;
    }

    public TaskInfoView getTaskInfoView() {
        return taskInfo;
    }

    public TaskProgressView getProgress() {
        return progress;
    }

    public TaskErrorView getError() {
        return error;
    }
}