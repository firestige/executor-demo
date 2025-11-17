package xyz.firestige.executor.domain.task;

import xyz.firestige.executor.execution.StageResult;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.state.event.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单租户 Task 聚合（DDD 重构：充血模型）
 *
 * 职责：
 * 1. 管理任务生命周期和状态转换
 * 2. 保护业务不变式
 * 3. 封装业务行为
 */
public class TaskAggregate {

    private String taskId;
    private String planId;
    private String tenantId;

    private Long deployUnitId;
    private Long deployUnitVersion;
    private String deployUnitName;

    private TaskStatus status;
    private int currentStageIndex;
    private int retryCount;
    private Integer maxRetry; // null 表示使用全局
    private int totalStages; // Stage 总数

    private TaskCheckpoint checkpoint;
    private List<StageResult> stageResults = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    private TenantDeployConfigSnapshot prevConfigSnapshot; // 上一次可用配置快照
    private Long lastKnownGoodVersion; // 上一次成功切换完成的版本号
    private Long durationMillis; // 任务持续时长（毫秒）

    // 运行时标志
    private boolean pauseRequested;
    private String cancelledBy;

    // ============================================
    // RF-11: 领域事件收集
    // ============================================
    private final List<TaskStatusEvent> domainEvents = new ArrayList<>();

    public TaskAggregate(String taskId, String planId, String tenantId) {
        this.taskId = taskId;
        this.planId = planId;
        this.tenantId = tenantId;
        this.status = TaskStatus.CREATED;
    }

    // ============================================
    // RF-11: 事件管理方法
    // ============================================

    /**
     * 获取聚合产生的领域事件（不可修改）
     */
    public List<TaskStatusEvent> getDomainEvents() {
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
    private void addDomainEvent(TaskStatusEvent event) {
        this.domainEvents.add(event);
    }

    // ============================================
    // 业务行为方法（DDD 重构新增）
    // ============================================

    /**
     * 标记为 PENDING（准备执行）
     * 不变式：只有 CREATED 状态可以标记为 PENDING
     */
    public void markAsPending() {
        if (status != TaskStatus.CREATED) {
            throw new IllegalStateException(
                String.format("只有 CREATED 状态可以标记为 PENDING，当前状态: %s, taskId: %s", status, taskId)
            );
        }
        this.status = TaskStatus.PENDING;
    }

    /**
     * 启动任务
     * 不变式：只有 PENDING 状态可以启动
     */
    public void start() {
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException(
                String.format("只有 PENDING 状态的任务可以启动，当前状态: %s, taskId: %s", status, taskId)
            );
        }
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();

        // ✅ 产生领域事件
        TaskStartedEvent event = new TaskStartedEvent(taskId, totalStages);
        addDomainEvent(event);
    }

    /**
     * 请求暂停（协作式，在 Stage 边界生效）
     * 不变式：只有 RUNNING 状态可以请求暂停
     */
    public void requestPause() {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                String.format("只有 RUNNING 状态可以请求暂停，当前状态: %s, taskId: %s", status, taskId)
            );
        }
        this.pauseRequested = true;
    }

    /**
     * 在 Stage 边界应用暂停
     * 由执行器在 Stage 边界调用
     */
    public void applyPauseAtStageBoundary() {
        if (pauseRequested && status == TaskStatus.RUNNING) {
            this.status = TaskStatus.PAUSED;
            this.pauseRequested = false;

            // ✅ 产生领域事件
            TaskPausedEvent event = new TaskPausedEvent();
            addDomainEvent(event);
        }
    }

    /**
     * 恢复执行
     * 不变式：只有 PAUSED 状态可以恢复
     */
    public void resume() {
        if (status != TaskStatus.PAUSED) {
            throw new IllegalStateException(
                String.format("只有 PAUSED 状态可以恢复，当前状态: %s, taskId: %s", status, taskId)
            );
        }
        this.status = TaskStatus.RUNNING;
        this.pauseRequested = false;

        // ✅ 产生领域事件
        TaskResumedEvent event = new TaskResumedEvent();
        addDomainEvent(event);
    }

    /**
     * 取消任务
     * 不变式：终态任务不能取消
     */
    public void cancel(String cancelledBy) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                String.format("终态任务无法取消，当前状态: %s, taskId: %s", status, taskId)
            );
        }
        this.status = TaskStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.endedAt = LocalDateTime.now();
        calculateDuration();

        // ✅ 产生领域事件
        TaskCancelledEvent event = new TaskCancelledEvent(taskId);
        event.setCancelledBy(cancelledBy);
        addDomainEvent(event);
    }

    /**
     * 完成当前 Stage
     * 不变式：必须处于 RUNNING 状态
     */
    public void completeStage(StageResult result) {
        validateCanCompleteStage();

        this.stageResults.add(result);
        this.currentStageIndex++;

        // 检查是否所有 Stage 完成
        if (isAllStagesCompleted()) {
            complete();
        }
    }

    /**
     * Stage 失败
     * 不变式：必须处于 RUNNING 状态
     */
    public void failStage(StageResult result) {
        validateCanCompleteStage();

        this.stageResults.add(result);
        this.status = TaskStatus.FAILED;
        this.endedAt = LocalDateTime.now();
        calculateDuration();

        // ✅ 产生领域事件
        TaskFailedEvent event = new TaskFailedEvent();
        addDomainEvent(event);
    }

    /**
     * 判断是否所有 Stage 完成
     */
    public boolean isAllStagesCompleted() {
        return currentStageIndex >= totalStages;
    }

    /**
     * 完成任务
     */
    private void complete() {
        this.status = TaskStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
        calculateDuration();

        // ✅ 产生领域事件
        TaskCompletedEvent event = new TaskCompletedEvent();
        addDomainEvent(event);
    }

    /**
     * 重试任务
     * 不变式：只有 FAILED 或 ROLLED_BACK 状态可以重试
     */
    public void retry(boolean fromCheckpoint, Integer globalMaxRetry) {
        if (status != TaskStatus.FAILED && status != TaskStatus.ROLLED_BACK) {
            throw new IllegalStateException(
                String.format("只有 FAILED 或 ROLLED_BACK 状态可以重试，当前状态: %s, taskId: %s", status, taskId)
            );
        }

        // 检查重试次数限制
        int effectiveMaxRetry = maxRetry != null ? maxRetry : (globalMaxRetry != null ? globalMaxRetry : Integer.MAX_VALUE);
        if (retryCount >= effectiveMaxRetry) {
            throw new IllegalStateException(
                String.format("已达最大重试次数 %d，taskId: %s", effectiveMaxRetry, taskId)
            );
        }

        this.status = TaskStatus.RUNNING;
        this.retryCount++;
        this.endedAt = null;
        this.durationMillis = null;

        // 如果不是从 checkpoint 重试，清空进度
        if (!fromCheckpoint) {
            this.currentStageIndex = 0;
            this.stageResults.clear();
        }

        // ✅ 产生领域事件
        TaskRetryStartedEvent event = new TaskRetryStartedEvent(taskId, fromCheckpoint);
        addDomainEvent(event);
    }

    /**
     * 开始回滚
     * 不变式：必须有可用的回滚快照
     */
    public void startRollback(String reason) {
        if (prevConfigSnapshot == null) {
            throw new IllegalStateException(
                String.format("无可用的回滚快照，taskId: %s", taskId)
            );
        }
        this.status = TaskStatus.ROLLING_BACK;

        // ✅ 产生领域事件
        TaskRollingBackEvent event = new TaskRollingBackEvent(taskId, reason, null);
        addDomainEvent(event);
    }

    /**
     * 完成回滚
     * 不变式：必须处于 ROLLING_BACK 状态
     */
    public void completeRollback() {
        if (status != TaskStatus.ROLLING_BACK) {
            throw new IllegalStateException(
                String.format("非 ROLLING_BACK 状态无法完成回滚，当前状态: %s, taskId: %s", status, taskId)
            );
        }
        this.status = TaskStatus.ROLLED_BACK;
        this.endedAt = LocalDateTime.now();
        calculateDuration();

        // ✅ 产生领域事件
        TaskRolledBackEvent event = new TaskRolledBackEvent(taskId, null);
        if (prevConfigSnapshot != null) {
            event.setPrevDeployUnitVersion(prevConfigSnapshot.getDeployUnitVersion());
        }
        addDomainEvent(event);
    }

    /**
     * 回滚失败
     * 不变式：必须处于 ROLLING_BACK 状态
     */
    public void failRollback(String reason) {
        if (status != TaskStatus.ROLLING_BACK) {
            throw new IllegalStateException(
                String.format("非 ROLLING_BACK 状态无法标记回滚失败，当前状态: %s, taskId: %s", status, taskId)
            );
        }
        this.status = TaskStatus.ROLLBACK_FAILED;
        this.endedAt = LocalDateTime.now();
        calculateDuration();

        // ✅ 产生领域事件
        TaskRollbackFailedEvent event = new TaskRollbackFailedEvent();
        event.setMessage("回滚失败: " + reason);
        addDomainEvent(event);
    }

    /**
     * 标记为失败（通用）
     */
    public void markAsFailed() {
        if (status.isTerminal()) {
            // 已经是终态，不再更改
            return;
        }
        this.status = TaskStatus.FAILED;
        this.endedAt = LocalDateTime.now();
        calculateDuration();
    }

    // ============================================
    // 不变式保护（私有方法）
    // ============================================

    /**
     * 验证是否可以完成 Stage
     */
    private void validateCanCompleteStage() {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                String.format("非 RUNNING 状态无法完成 Stage，当前状态: %s, taskId: %s", status, taskId)
            );
        }
    }

    /**
     * 计算任务持续时长
     */
    private void calculateDuration() {
        if (startedAt != null && endedAt != null) {
            this.durationMillis = Duration.between(startedAt, endedAt).toMillis();
        }
    }

    // ============================================
    // Getter/Setter（保留必要的）
    // ============================================

    public String getTaskId() { return taskId; }
    public String getPlanId() { return planId; }
    public String getTenantId() { return tenantId; }

    public Long getDeployUnitId() { return deployUnitId; }
    public void setDeployUnitId(Long deployUnitId) { this.deployUnitId = deployUnitId; }
    public Long getDeployUnitVersion() { return deployUnitVersion; }
    public void setDeployUnitVersion(Long deployUnitVersion) { this.deployUnitVersion = deployUnitVersion; }
    public String getDeployUnitName() { return deployUnitName; }
    public void setDeployUnitName(String deployUnitName) { this.deployUnitName = deployUnitName; }

    public TaskStatus getStatus() { return status; }

    /**
     * 直接设置状态（内部使用，逐步淘汰）
     * @deprecated 请使用业务方法：start(), pause(), resume(), cancel() 等
     */
    @Deprecated
    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public int getCurrentStageIndex() { return currentStageIndex; }

    /**
     * 直接设置 Stage 索引（内部使用，逐步淘汰）
     * @deprecated 请使用 completeStage() 或 failStage()
     */
    @Deprecated
    public void setCurrentStageIndex(int currentStageIndex) {
        this.currentStageIndex = currentStageIndex;
    }

    public int getRetryCount() { return retryCount; }

    public Integer getMaxRetry() { return maxRetry; }
    public void setMaxRetry(Integer maxRetry) { this.maxRetry = maxRetry; }

    public int getTotalStages() { return totalStages; }
    public void setTotalStages(int totalStages) { this.totalStages = totalStages; }

    public TaskCheckpoint getCheckpoint() { return checkpoint; }
    public void setCheckpoint(TaskCheckpoint checkpoint) { this.checkpoint = checkpoint; }

    public List<StageResult> getStageResults() { return stageResults; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }

    /**
     * 直接设置开始时间（内部使用，逐步淘汰）
     * @deprecated 由 start() 方法自动设置
     */
    @Deprecated
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() { return endedAt; }

    /**
     * 直接设置结束时间（内部使用，逐步淘汰）
     * @deprecated 由 complete()、cancel() 等方法自动设置
     */
    @Deprecated
    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public TenantDeployConfigSnapshot getPrevConfigSnapshot() { return prevConfigSnapshot; }
    public void setPrevConfigSnapshot(TenantDeployConfigSnapshot prevConfigSnapshot) {
        this.prevConfigSnapshot = prevConfigSnapshot;
    }

    public Long getLastKnownGoodVersion() { return lastKnownGoodVersion; }
    public void setLastKnownGoodVersion(Long lastKnownGoodVersion) {
        this.lastKnownGoodVersion = lastKnownGoodVersion;
    }

    public Long getDurationMillis() { return durationMillis; }

    /**
     * 直接设置持续时长（内部使用，逐步淘汰）
     * @deprecated 由 calculateDuration() 自动计算
     */
    @Deprecated
    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public boolean isPauseRequested() { return pauseRequested; }
    public String getCancelledBy() { return cancelledBy; }
}




