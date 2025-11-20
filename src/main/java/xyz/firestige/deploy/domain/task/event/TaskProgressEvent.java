package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

/**
 * 任务进度事件
 */
public class TaskProgressEvent extends TaskStatusEvent {

    /**
     * 当前执行的 Stage
     */
    private final String currentStage;

    /**
     * 已完成的 Stage 数量
     */
    private final int completedStages;

    /**
     * 总 Stage 数量
     */
    private final int totalStages;

    /**
     * 进度百分比（0-100）
     */
    private final double progress;

    public TaskProgressEvent(TaskInfo info, String currentStage, int completedStages, int totalStages) {
        super(info);
        this.currentStage = currentStage;
        this.completedStages = completedStages;
        this.totalStages = totalStages;
        this.progress = totalStages > 0 ? (double) completedStages / totalStages * 100 : 0;
        setMessage("任务执行中，进度: " + String.format("%.1f", progress) + "%");
    }

    // Getters and Setters

    public String getCurrentStage() {
        return currentStage;
    }

    public int getCompletedStages() {
        return completedStages;
    }

    public int getTotalStages() {
        return totalStages;
    }

    public double getProgress() {
        return progress;
    }
}

