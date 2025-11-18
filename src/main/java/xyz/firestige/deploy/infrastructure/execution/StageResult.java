package xyz.firestige.deploy.infrastructure.execution;

import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.infrastructure.execution.stage.StepResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Stage 执行结果
 */
public class StageResult {

    /**
     * Stage 名称
     */
    private String stageName;

    /**
     * 执行状态
     */
    private StageStatus status;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * Step 结果输出
     */
    private final List<StepResult> stepResults = new ArrayList<>();

    /**
     * 失败信息（如果失败）
     */
    private FailureInfo failureInfo;

    /**
     * 执行耗时
     */
    private Duration duration;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    public StageResult(String stageName) {
        this.stageName = stageName;
        this.startTime = LocalDateTime.now();
    }

    public StageResult(String stageName, StageStatus status, boolean success) {
        this.stageName = stageName;
        this.status = status;
        this.success = success;
    }

    /**
     * 创建结果占位符
     */
    public static StageResult start(String stageName) {
        return new StageResult(stageName);
    }

    /**
     * 创建成功结果
     */
    public static StageResult success(String stageName) {
        return new StageResult(stageName, StageStatus.COMPLETED, true);
    }

    /**
     * 创建失败结果
     */
    public static StageResult failure(String stageName, FailureInfo failureInfo) {
        StageResult result = new StageResult(stageName, StageStatus.FAILED, false);
        result.setFailureInfo(failureInfo);
        return result;
    }

    /**
     * 计算耗时
     */
    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            this.duration = Duration.between(startTime, endTime);
        }
    }

    public void success() {
        this.success = true;
        this.status = StageStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
        calculateDuration();
    }

    public void failure(FailureInfo failureInfo) {
        this.success = false;
        this.status = StageStatus.FAILED;
        this.endTime = LocalDateTime.now();
        this.failureInfo = failureInfo;
        calculateDuration();
    }

    // Getters and Setters

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public StageStatus getStatus() {
        return status;
    }

    public void setStatus(StageStatus status) {
        this.status = status;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<StepResult> getStepResults() {
        return stepResults;
    }

    public void addStepResult(StepResult stepResult) {
        this.stepResults.add(stepResult);
    }

    public void addStepResults(List<StepResult> stepResults) {
        this.stepResults.addAll(stepResults);
    }

    public void consumeStepResults(Consumer<List<StepResult>> consumer) {
        consumer.accept(stepResults);
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }

    public void setFailureInfo(FailureInfo failureInfo) {
        this.failureInfo = failureInfo;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "StageResult{" +
                "stageName='" + stageName + '\'' +
                ", status=" + status +
                ", success=" + success +
                ", duration=" + duration +
                '}';
    }
}

