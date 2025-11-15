package xyz.firestige.executor.domain.plan;

import xyz.firestige.executor.domain.task.TaskAggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 计划聚合（Plan）
 * 代表一组租户 Task 的执行批次。
 */
public class PlanAggregate {

    private String planId;
    private String version;
    private PlanStatus status;
    private Integer maxConcurrency; // 可为空，表示使用全局配置

    private final List<TaskAggregate> tasks = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    private String failureSummary;
    private double progress;

    public PlanAggregate(String planId) {
        this.planId = planId;
        this.status = PlanStatus.CREATED;
        this.createdAt = LocalDateTime.now();
    }

    public void addTask(TaskAggregate task) {
        if (task != null) {
            this.tasks.add(task);
        }
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
    }

    public Integer getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(Integer maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public List<TaskAggregate> getTasks() {
        return tasks;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public String getFailureSummary() {
        return failureSummary;
    }

    public void setFailureSummary(String failureSummary) {
        this.failureSummary = failureSummary;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }
}

