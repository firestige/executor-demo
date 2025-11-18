package xyz.firestige.deploy.state.event;

import xyz.firestige.deploy.state.TaskStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务完成事件
 */
public class TaskCompletedEvent extends TaskStatusEvent {

    /**
     * 执行耗时
     */
    private Duration duration;

    /**
     * 已完成的 Stage 列表
     */
    private List<String> completedStages;

    public TaskCompletedEvent() {
        super();
        setStatus(TaskStatus.COMPLETED);
        this.completedStages = new ArrayList<>();
    }

    public TaskCompletedEvent(String taskId, Duration duration, List<String> completedStages) {
        super(taskId, TaskStatus.COMPLETED);
        this.duration = duration;
        this.completedStages = completedStages != null ? completedStages : new ArrayList<>();
        setMessage("任务执行完成，耗时: " + duration + ", Stage 数: " + this.completedStages.size());
    }

    // Getters and Setters

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public List<String> getCompletedStages() {
        return completedStages;
    }

    public void setCompletedStages(List<String> completedStages) {
        this.completedStages = completedStages;
    }
}

