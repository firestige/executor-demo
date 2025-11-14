package xyz.firestige.executor.facade;

import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.checkpoint.Checkpoint;
import xyz.firestige.executor.state.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务状态查询结果
 */
public class TaskStatusInfo {

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 进度（0-100）
     */
    private double progress;

    /**
     * 当前执行的 Stage
     */
    private String currentStage;

    /**
     * 已完成的 Stage 列表
     */
    private List<String> completedStages;

    /**
     * 失败信息（如果失败）
     */
    private FailureInfo failureInfo;

    /**
     * 检查点信息（如果有）
     */
    private Checkpoint checkpoint;

    /**
     * 消息
     */
    private String message;

    public TaskStatusInfo() {
        this.completedStages = new ArrayList<>();
    }

    public TaskStatusInfo(String taskId, TaskStatus status) {
        this.taskId = taskId;
        this.status = status;
        this.completedStages = new ArrayList<>();
    }

    /**
     * 添加已完成的 Stage
     */
    public void addCompletedStage(String stageName) {
        this.completedStages.add(stageName);
    }

    /**
     * 计算进度
     */
    public void calculateProgress(int totalStages) {
        if (totalStages > 0) {
            this.progress = (double) completedStages.size() / totalStages * 100;
        }
    }

    // Getters and Setters

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public List<String> getCompletedStages() {
        return completedStages;
    }

    public void setCompletedStages(List<String> completedStages) {
        this.completedStages = completedStages;
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }

    public void setFailureInfo(FailureInfo failureInfo) {
        this.failureInfo = failureInfo;
    }

    public Checkpoint getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Checkpoint checkpoint) {
        this.checkpoint = checkpoint;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "TaskStatusInfo{" +
                "taskId='" + taskId + '\'' +
                ", status=" + status +
                ", progress=" + progress +
                ", currentStage='" + currentStage + '\'' +
                ", completedStagesCount=" + completedStages.size() +
                '}';
    }
}

