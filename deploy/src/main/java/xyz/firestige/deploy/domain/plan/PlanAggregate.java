package xyz.firestige.deploy.domain.plan;

import xyz.firestige.deploy.domain.plan.event.PlanCompletedEvent;
import xyz.firestige.deploy.domain.plan.event.PlanFailedEvent;
import xyz.firestige.deploy.domain.plan.event.PlanPausedEvent;
import xyz.firestige.deploy.domain.plan.event.PlanReadyEvent;
import xyz.firestige.deploy.domain.plan.event.PlanResumedEvent;
import xyz.firestige.deploy.domain.plan.event.PlanStartedEvent;
import xyz.firestige.deploy.domain.plan.event.PlanStatusEvent;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 计划聚合（Plan）- DDD 重构：充血模型 + 修正聚合边界 + 领域事件（RF-11）
 * <p>
 * 职责：
 * 1. 管理 Plan 生命周期和状态转换
 * 2. 管理 Task ID 列表（聚合间通过 ID 引用）
 * 3. 保护业务不变式
 * 4. 产生领域事件（RF-11）
 * <p>
 * DDD 原则：聚合间通过 ID 引用，不直接持有其他聚合对象
 */
public class PlanAggregate {

    private final PlanId planId;
    private String version;
    private PlanStatus status;
    private Integer maxConcurrency; // 可为空，表示使用全局配置

    // ✅ DDD 重构：改为持有 Task ID 列表，而非 Task 对象
    private final List<TaskId> taskIds = new ArrayList<>();

    private final LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    private String failureSummary;
    private double progress;

    // ============================================
    // RF-11: 领域事件收集
    // ============================================
    private final List<PlanStatusEvent> domainEvents = new ArrayList<>();

    public PlanAggregate(PlanId planId) {
        this.planId = planId;
        this.status = PlanStatus.CREATED;
        this.createdAt = LocalDateTime.now();
    }

    // ============================================
    // RF-11: 事件管理方法
    // ============================================

    /**
     * 获取聚合产生的领域事件（不可修改）
     */
    public List<PlanStatusEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 清空领域事件（发布后调用）
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    /**
     * 添加领域事件（私有方法）
     */
    private void addDomainEvent(PlanStatusEvent event) {
        this.domainEvents.add(event);
    }

    // ============================================
    // 业务行为方法（DDD 重构新增）
    // ============================================

    /**
     * 添加任务到 Plan（RF-07 重构：只持有 ID）
     * 不变式：Plan 已启动后不能添加任务，不能添加重复任务
     *
     * @param taskId Task ID
     */
    public void addTask(TaskId taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID 不能为空");
        }

        if (status != PlanStatus.CREATED && status != PlanStatus.READY) {
            throw new IllegalStateException(
                String.format("Plan 已启动，无法添加任务，当前状态: %s, planId: %s", status, planId)
            );
        }

        // ✅ 检查是否已存在（通过 ID）
        if (taskIds.contains(taskId)) {
            throw new IllegalArgumentException(
                String.format("任务已存在: %s, planId: %s", taskId, planId)
            );
        }

        // ✅ 只添加 ID
        this.taskIds.add(taskId);
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

        if (taskIds.isEmpty()) {
            throw new IllegalStateException(
                String.format("Plan 没有关联任务，无法标记为 READY，planId: %s", planId)
            );
        }

        this.status = PlanStatus.READY;

        // ✅ RF-11: 产生领域事件
        PlanReadyEvent event = new PlanReadyEvent(PlanInfo.from(this), taskIds.size());
        addDomainEvent(event);
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

        if (taskIds.isEmpty()) {
            throw new IllegalStateException(
                String.format("Plan 没有关联任务，无法启动，planId: %s", planId)
            );
        }

        this.status = PlanStatus.RUNNING;
        this.startedAt = LocalDateTime.now();

        // ✅ RF-11: 产生领域事件
        PlanStartedEvent event = new PlanStartedEvent(PlanInfo.from(this), taskIds.size());
        addDomainEvent(event);
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

        // ✅ RF-11: 产生领域事件
        PlanPausedEvent event = new PlanPausedEvent(PlanInfo.from(this));
        addDomainEvent(event);
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

        // ✅ RF-11: 产生领域事件
        PlanResumedEvent event = new PlanResumedEvent(PlanInfo.from(this));
        addDomainEvent(event);
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

        // ✅ RF-11: 产生领域事件
        PlanCompletedEvent event = new PlanCompletedEvent(PlanInfo.from(this), taskIds.size());
        addDomainEvent(event);
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

        // ✅ RF-11: 产生领域事件
        PlanFailedEvent event = new PlanFailedEvent(PlanInfo.from(this), failureSummary);
        addDomainEvent(event);
    }

    // ============================================
    // 查询方法（业务逻辑）
    // ============================================

    /**
     * 获取任务数量
     */
    public int getTaskCount() {
        return taskIds.size();
    }

    /**
     * 判断是否可以启动
     */
    public boolean canStart() {
        return status == PlanStatus.READY && !taskIds.isEmpty();
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

    public PlanId getPlanId() {
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

    public Integer getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(Integer maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    /**
     * 获取任务 ID 列表（RF-07 重构：返回不可变列表）
     *
     * @return 任务 ID 列表（不可变）
     */
    public List<TaskId> getTaskIds() {
        return Collections.unmodifiableList(taskIds);
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

