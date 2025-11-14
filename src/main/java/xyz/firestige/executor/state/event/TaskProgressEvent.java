package xyz.firestige.executor.state.event;

import xyz.firestige.executor.state.TaskStatus;

/**
 * 任务进度事件
 */
public class TaskProgressEvent extends TaskStatusEvent {

    /**
     * 当前执行的 Stage
     */
    private String currentStage;

    /**
     * 已完成的 Stage 数量
     */
    private int completedStages;

    /**
     * 总 Stage 数量
     */
    private int totalStages;

    /**
     * 进度百分比（0-100）
     */
    private double progress;

    public TaskProgressEvent() {
        super();
        setStatus(TaskStatus.RUNNING);
    }

    public TaskProgressEvent(String taskId, String currentStage, int completedStages, int totalStages) {
        super(taskId, TaskStatus.RUNNING);
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

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
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

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }
}

