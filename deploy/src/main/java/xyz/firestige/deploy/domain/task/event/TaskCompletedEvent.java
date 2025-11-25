package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

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
    private final Duration duration;

    /**
     * 已完成的 Stage 列表
     */
    private final List<String> completedStages;

    public TaskCompletedEvent(TaskInfo info, Duration duration, List<String> completedStages) {
        super(info);
        this.duration = duration;
        this.completedStages = completedStages != null ? completedStages : new ArrayList<>();
        setMessage("任务执行完成，耗时: " + duration + ", Stage 数: " + this.completedStages.size());
    }

    // Getters and Setters

    public Duration getDuration() {
        return duration;
    }

    public List<String> getCompletedStages() {
        return completedStages;
    }
}

