package xyz.firestige.executor.execution;

import xyz.firestige.executor.exception.FailureInfo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline 执行结果
 */
public class PipelineResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 已完成的 Stage 结果列表
     */
    private List<StageResult> completedStages;

    /**
     * 失败的 Stage 结果（如果有）
     */
    private StageResult failedStage;

    /**
     * 总耗时
     */
    private Duration totalDuration;

    /**
     * 失败信息
     */
    private FailureInfo failureInfo;

    public PipelineResult() {
        this.completedStages = new ArrayList<>();
    }

    public PipelineResult(boolean success) {
        this.success = success;
        this.completedStages = new ArrayList<>();
    }

    /**
     * 创建成功结果
     */
    public static PipelineResult success(List<StageResult> completedStages) {
        PipelineResult result = new PipelineResult(true);
        result.setCompletedStages(completedStages);
        return result;
    }

    /**
     * 创建失败结果
     */
    public static PipelineResult failure(List<StageResult> completedStages, StageResult failedStage, FailureInfo failureInfo) {
        PipelineResult result = new PipelineResult(false);
        result.setCompletedStages(completedStages);
        result.setFailedStage(failedStage);
        result.setFailureInfo(failureInfo);
        return result;
    }

    /**
     * 添加 Stage 结果
     */
    public void addStageResult(StageResult stageResult) {
        this.completedStages.add(stageResult);
    }

    /**
     * 获取成功的 Stage 数量
     */
    public int getSuccessCount() {
        return (int) completedStages.stream()
                .filter(StageResult::isSuccess)
                .count();
    }

    /**
     * 获取失败的 Stage 数量
     */
    public int getFailureCount() {
        return (int) completedStages.stream()
                .filter(result -> !result.isSuccess())
                .count();
    }

    /**
     * 计算总耗时
     */
    public void calculateTotalDuration() {
        long totalMillis = completedStages.stream()
                .filter(result -> result.getDuration() != null)
                .mapToLong(result -> result.getDuration().toMillis())
                .sum();

        if (failedStage != null && failedStage.getDuration() != null) {
            totalMillis += failedStage.getDuration().toMillis();
        }

        this.totalDuration = Duration.ofMillis(totalMillis);
    }

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<StageResult> getCompletedStages() {
        return completedStages;
    }

    public void setCompletedStages(List<StageResult> completedStages) {
        this.completedStages = completedStages;
    }

    public StageResult getFailedStage() {
        return failedStage;
    }

    public void setFailedStage(StageResult failedStage) {
        this.failedStage = failedStage;
    }

    public Duration getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(Duration totalDuration) {
        this.totalDuration = totalDuration;
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }

    public void setFailureInfo(FailureInfo failureInfo) {
        this.failureInfo = failureInfo;
    }

    @Override
    public String toString() {
        return "PipelineResult{" +
                "success=" + success +
                ", completedStagesCount=" + completedStages.size() +
                ", totalDuration=" + totalDuration +
                '}';
    }
}

