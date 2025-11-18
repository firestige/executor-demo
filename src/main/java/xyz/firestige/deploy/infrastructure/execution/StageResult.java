package xyz.firestige.deploy.infrastructure.execution;

import xyz.firestige.deploy.domain.shared.exception.FailureInfo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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
     * 输出数据（传递给下一个 Stage）
     */
    private Map<String, Object> output;

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

    public StageResult() {
        this.output = new HashMap<>();
    }

    public StageResult(String stageName, StageStatus status, boolean success) {
        this.stageName = stageName;
        this.status = status;
        this.success = success;
        this.output = new HashMap<>();
    }

    /**
     * 创建成功结果
     */
    public static StageResult success(String stageName) {
        return new StageResult(stageName, StageStatus.COMPLETED, true);
    }

    /**
     * 创建成功结果（带输出）
     */
    public static StageResult success(String stageName, Map<String, Object> output) {
        StageResult result = success(stageName);
        result.setOutput(output);
        return result;
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
     * 创建跳过结果
     */
    public static StageResult skipped(String stageName, String reason) {
        StageResult result = new StageResult(stageName, StageStatus.SKIPPED, true);
        result.addOutput("skipReason", reason);
        return result;
    }

    /**
     * 添加输出数据
     */
    public void addOutput(String key, Object value) {
        this.output.put(key, value);
    }

    /**
     * 计算耗时
     */
    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            this.duration = Duration.between(startTime, endTime);
        }
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

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
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

