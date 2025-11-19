package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.execution.StageResult;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Stage 完成事件（RF-18: 支持进度信息）
 */
public class TaskStageCompletedEvent extends TaskStatusEvent {

    /**
     * Stage 名称
     */
    private String stageName;

    /**
     * Stage 执行结果（旧版构造函数使用）
     */
    private StageResult stageResult;

    /**
     * RF-18: 已完成的 Stage 数量
     */
    private int completedStages;

    /**
     * RF-18: 总 Stage 数量
     */
    private int totalStages;

    /**
     * RF-18: 执行时长
     */
    private Duration duration;

    public TaskStageCompletedEvent() {
        super();
        setStatus(TaskStatus.RUNNING);
    }

    /**
     * 旧版构造函数（保持兼容）
     */
    public TaskStageCompletedEvent(TaskId taskId, String stageName, StageResult stageResult) {
        super(taskId, TaskStatus.RUNNING);
        this.stageName = stageName;
        this.stageResult = stageResult;
        setMessage("Stage 执行完成: " + stageName);
    }

    /**
     * RF-18: 新构造函数（带进度信息）
     */
    public TaskStageCompletedEvent(
            TaskId taskId,
            String stageName,
            int completedStages,
            int totalStages,
            Duration duration,
            LocalDateTime occurredOn) {
        super(taskId, TaskStatus.RUNNING);
        this.stageName = stageName;
        this.completedStages = completedStages;
        this.totalStages = totalStages;
        this.duration = duration;
        setMessage("Stage 执行完成: " + stageName);
    }

    // Getters and Setters

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public StageResult getStageResult() {
        return stageResult;
    }

    public void setStageResult(StageResult stageResult) {
        this.stageResult = stageResult;
    }

    public int getCompletedStages() {
        return completedStages;
    }

    public void setCompletedStages(int completedStages) {
        this.completedStages = completedStages;
    }

    public int getTotalStages() {
        return totalStages;
    }

    public void setTotalStages(int totalStages) {
        this.totalStages = totalStages;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    /**
     * 计算完成百分比
     */
    public double getPercentage() {
        return totalStages == 0 ? 0 : (completedStages * 100.0 / totalStages);
    }
}

