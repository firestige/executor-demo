package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;
import xyz.firestige.deploy.domain.task.TaskProgressView;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务完成事件
 * <p>
 * T-036: 重构为使用三大视图
 * - TaskInfoView: 基本信息
 * - TaskProgressView: 进度信息（可选）
 */
public class TaskCompletedEvent extends TaskStatusEvent {

    // T-036: 使用视图
    private final TaskInfoView taskInfo;
    private final TaskProgressView progress;

    /**
     * 执行耗时
     */
    private final Duration duration;

    /**
     * 已完成的 Stage 列表
     */
    private final List<String> completedStages;

    // ============================================
    // 新构造器（T-036）
    // ============================================

    /**
     * 新构造器：接受 TaskAggregate
     *
     * @param task Task 聚合根
     * @param duration 执行耗时
     * @param completedStages 已完成的 Stage 列表
     * @since T-036
     */
    public TaskCompletedEvent(TaskAggregate task, Duration duration, List<String> completedStages) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.progress = task.getProgressView();
        this.duration = duration;
        this.completedStages = completedStages != null ? completedStages : new ArrayList<>();
        setMessage("任务执行完成，耗时: " + duration + ", Stage 数: " + this.completedStages.size());
    }

    // ============================================
    // 旧构造器（向后兼容）
    // ============================================

    /**
     * 旧构造器：接受 TaskInfo
     *
     * @param info TaskInfo 对象
     * @param duration 执行耗时
     * @param completedStages 已完成的 Stage 列表
     * @deprecated 使用 TaskCompletedEvent(TaskAggregate task, Duration, List) 代替
     */
    @Deprecated
    public TaskCompletedEvent(TaskInfo info, Duration duration, List<String> completedStages) {
        super(info);
        this.taskInfo = getTaskInfo();  // 从父类获取
        this.progress = null;  // 旧方式无进度信息
        this.duration = duration;
        this.completedStages = completedStages != null ? completedStages : new ArrayList<>();
        setMessage("任务执行完成，耗时: " + duration + ", Stage 数: " + this.completedStages.size());
    }

    // ============================================
    // Getters
    // ============================================

    public Duration getDuration() {
        return duration;
    }

    public List<String> getCompletedStages() {
        return completedStages;
    }

    public TaskInfoView getTaskInfoView() {
        return taskInfo;
    }

    public TaskProgressView getProgress() {
        return progress;
    }
}

