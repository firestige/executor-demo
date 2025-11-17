package xyz.firestige.executor.domain.plan;

import xyz.firestige.executor.domain.task.TaskAggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 计划聚合（Plan）- DDD 重构：充血模型
 *
 * 职责：
 * 1. 管理 Plan 生命周期和状态转换
 * 2. 管理 Task 列表
 * 3. 保护业务不变式
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

    // ============================================
    // 业务行为方法（DDD 重构新增）
    // ============================================

    /**
     * 添加任务到 Plan
     * 不变式：Plan 已启动后不能添加任务，不能添加重复任务
     */
    public void addTask(TaskAggregate task) {
        if (task == null) {
            throw new IllegalArgumentException("Task 不能为 null");
        }

        if (status != PlanStatus.CREATED && status != PlanStatus.READY) {
            throw new IllegalStateException(
                String.format("Plan 已启动，无法添加任务，当前状态: %s, planId: %s", status, planId)
            );
        }

        // 检查是否已存在
        boolean exists = tasks.stream()
            .anyMatch(t -> t.getTaskId().equals(task.getTaskId()));
        if (exists) {
            throw new IllegalArgumentException(
                String.format("任务已存在: %s, planId: %s", task.getTaskId(), planId)
            );
        }

        this.tasks.add(task);
    }

    /**
     * 标记 Plan 为 READY（准备启动）
     * 不变式：必须有至少一个任务
     */
    public void markAsReady() {
        if (status != PlanStatus.CREATED) {
            throw new IllegalStateException(
                String.format("只有 CREATED 状态可以标记为 READY，当前状态: %s, planId: %s", status, planId)
            );
        }

        if (tasks.isEmpty()) {
            throw new IllegalStateException(
                String.format("Plan 没有关联任务，无法标记为 READY，planId: %s", planId)
            );
        }

        this.status = PlanStatus.READY;
    }

    /**
     * 启动 Plan
     * 不变式：必须处于 READY 状态且有任务
     */
    public void start() {
        if (status != PlanStatus.READY) {
            throw new IllegalStateException(
                String.format("只有 READY 状态可以启动，当前状态: %s, planId: %s", status, planId)
            );
        }

        if (tasks.isEmpty()) {
            throw new IllegalStateException(
                String.format("Plan 没有关联任务，无法启动，planId: %s", planId)
            );
        }

        this.status = PlanStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 暂停 Plan
     * 不变式：只有 RUNNING 状态可以暂停
     */
    public void pause() {
        if (status != PlanStatus.RUNNING) {
            throw new IllegalStateException(
                String.format("只有 RUNNING 状态可以暂停，当前状态: %s, planId: %s", status, planId)
            );
        }
        this.status = PlanStatus.PAUSED;
    }

    /**
     * 恢复 Plan
     * 不变式：只有 PAUSED 状态可以恢复
     */
    public void resume() {
        if (status != PlanStatus.PAUSED) {
            throw new IllegalStateException(
                String.format("只有 PAUSED 状态可以恢复，当前状态: %s, planId: %s", status, planId)
            );
        }
        this.status = PlanStatus.RUNNING;
    }

    /**
     * 完成 Plan
     * 不变式：只有 RUNNING 状态可以完成
     */
    public void complete() {
        if (status != PlanStatus.RUNNING) {
            throw new IllegalStateException(
                String.format("只有 RUNNING 状态可以完成，当前状态: %s, planId: %s", status, planId)
            );
        }
        this.status = PlanStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
    }

    /**
     * 标记 Plan 为失败
     */
    public void markAsFailed(String failureSummary) {
        if (status == PlanStatus.COMPLETED) {
            // 已完成不再更改
            return;
        }
        this.status = PlanStatus.FAILED;
        this.failureSummary = failureSummary;
        this.endedAt = LocalDateTime.now();
    }

    // ============================================
    // 查询方法（业务逻辑）
    // ============================================

    /**
     * 获取任务数量
     */
    public int getTaskCount() {
        return tasks.size();
    }

    /**
     * 判断是否可以启动
     */
    public boolean canStart() {
        return status == PlanStatus.READY && !tasks.isEmpty();
    }

    /**
     * 判断是否正在运行
     */
    public boolean isRunning() {
        return status == PlanStatus.RUNNING;
    }

    /**
     * 判断是否已暂停
     */
    public boolean isPaused() {
        return status == PlanStatus.PAUSED;
    }

    /**
     * 判断是否已完成（成功或失败）
     */
    public boolean isCompleted() {
        return status == PlanStatus.COMPLETED || status == PlanStatus.FAILED;
    }

    // ============================================
    // Getter/Setter（保留必要的）
    // ============================================

    public String getPlanId() {
        return planId;
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

    /**
     * 直接设置状态（内部使用，逐步淘汰）
     * @deprecated 请使用业务方法：start(), pause(), resume(), complete() 等
     */
    @Deprecated
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

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public String getFailureSummary() {
        return failureSummary;
    }


    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }
}

