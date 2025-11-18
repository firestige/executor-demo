package xyz.firestige.executor.domain.task;

import xyz.firestige.executor.domain.shared.vo.*;
import xyz.firestige.executor.domain.value.*;
import xyz.firestige.executor.execution.StageResult;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.state.event.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单租户 Task 聚合（DDD 重构：充血模型 + 值对象 RF-13）
 *
 * 职责：
 * 1. 管理任务生命周期和状态转换
 * 2. 保护业务不变式
 * 3. 封装业务行为
 * 4. 使用值对象增强类型安全（RF-13）
 */
public class TaskAggregate {

    // ============================================
    // RF-13: 使用值对象替换原始类型
    // ============================================
    private TaskId taskId;
    private PlanId planId;
    private TenantId tenantId;

    private DeployVersion deployVersion;
    private String deployUnitName;

    private TaskStatus status;
    private StageProgress stageProgress;
    private RetryPolicy retryPolicy;

    private TaskCheckpoint checkpoint;
    private List<StageResult> stageResults = new ArrayList<>();

    private TimeRange timeRange;

    private TenantDeployConfigSnapshot prevConfigSnapshot; // 上一次可用配置快照
    private Long lastKnownGoodVersion; // 上一次成功切换完成的版本号
    private TaskDuration duration;

    // 运行时标志
    private boolean pauseRequested;
    private String cancelledBy;

    // ============================================
    // RF-11: 领域事件收集
    // ============================================
    private final List<TaskStatusEvent> domainEvents = new ArrayList<>();

    public TaskAggregate(String taskId, String planId, String tenantId) {
        this.taskId = TaskId.of(taskId);
        this.planId = PlanId.of(planId);
        this.tenantId = TenantId.of(tenantId);
        this.status = TaskStatus.CREATED;
        this.timeRange = TimeRange.notStarted();
        this.duration = TaskDuration.notStarted();
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
                String.format("只有 CREATED 状态可以标记为 PENDING，当前状态: %s, taskId: %s", status, taskId.getValue())
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
                String.format("只有 PENDING 状态的任务可以启动，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }
        this.status = TaskStatus.RUNNING;
        this.timeRange = timeRange.start();

        // ✅ 产生领域事件
        TaskStartedEvent event = new TaskStartedEvent(taskId.getValue(), stageProgress.getTotalStages());
        addDomainEvent(event);
    }

    /**
     * 请求暂停（协作式，在 Stage 边界生效）
     * 不变式：只有 RUNNING 状态可以请求暂停
     */
    public void requestPause() {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                String.format("只有 RUNNING 状态可以请求暂停，当前状态: %s, taskId: %s", status, taskId.getValue())
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
                String.format("只有 PAUSED 状态可以恢复，当前状态: %s, taskId: %s", status, taskId.getValue())
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
                String.format("终态任务无法取消，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }
        this.status = TaskStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.timeRange = timeRange.end();
        calculateDuration();

        // ✅ 产生领域事件
        TaskCancelledEvent event = new TaskCancelledEvent(taskId.getValue());
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
        this.stageProgress = stageProgress.advance();

        // 检查是否所有 Stage 完成
        if (stageProgress.isCompleted()) {
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
        this.timeRange = timeRange.end();
        calculateDuration();

        // ✅ 产生领域事件
        TaskFailedEvent event = new TaskFailedEvent();
        addDomainEvent(event);
    }

    /**
     * 完成任务
     */
    private void complete() {
        this.status = TaskStatus.COMPLETED;
        this.timeRange = timeRange.end();
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
                String.format("只有 FAILED 或 ROLLED_BACK 状态可以重试，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }

        // RF-13: 使用 RetryPolicy 判断
        if (!retryPolicy.canRetry(globalMaxRetry)) {
            int effectiveMaxRetry = retryPolicy.getEffectiveMaxRetry(globalMaxRetry);
            throw new IllegalStateException(
                String.format("已达最大重试次数 %d，taskId: %s", effectiveMaxRetry, taskId.getValue())
            );
        }

        this.status = TaskStatus.RUNNING;
        this.retryPolicy = retryPolicy.incrementRetryCount();
        this.timeRange = timeRange.resetEnd();
        this.duration = TaskDuration.notStarted();

        // 如果不是从 checkpoint 重试，清空进度
        if (!fromCheckpoint) {
            this.stageProgress = stageProgress.reset();
            this.stageResults.clear();
        }

        // ✅ 产生领域事件
        TaskRetryStartedEvent event = new TaskRetryStartedEvent(taskId.getValue(), fromCheckpoint);
        addDomainEvent(event);
    }

    /**
     * 开始回滚
     * 不变式：必须有可用的回滚快照
     */
    public void startRollback(String reason) {
        if (prevConfigSnapshot == null) {
            throw new IllegalStateException(
                String.format("无可用的回滚快照，taskId: %s", taskId.getValue())
            );
        }
        this.status = TaskStatus.ROLLING_BACK;

        // ✅ 产生领域事件
        TaskRollingBackEvent event = new TaskRollingBackEvent(taskId.getValue(), reason, null);
        addDomainEvent(event);
    }

    /**
     * 完成回滚
     * 不变式：必须处于 ROLLING_BACK 状态
     */
    public void completeRollback() {
        if (status != TaskStatus.ROLLING_BACK) {
            throw new IllegalStateException(
                String.format("非 ROLLING_BACK 状态无法完成回滚，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }
        this.status = TaskStatus.ROLLED_BACK;
        this.timeRange = timeRange.end();
        calculateDuration();

        // ✅ 产生领域事件
        TaskRolledBackEvent event = new TaskRolledBackEvent(taskId.getValue(), null);
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
                String.format("非 ROLLING_BACK 状态无法标记回滚失败，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }
        this.status = TaskStatus.ROLLBACK_FAILED;
        this.timeRange = timeRange.end();
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
        this.timeRange = timeRange.end();
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
                String.format("非 RUNNING 状态无法完成 Stage，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }
    }

    /**
     * 计算任务持续时长
     */
    private void calculateDuration() {
        if (timeRange.getStartedAt() != null && timeRange.getEndedAt() != null) {
            this.duration = TaskDuration.between(timeRange.getStartedAt(), timeRange.getEndedAt());
        }
    }

    // ============================================
    // Getter/Setter（RF-13 重构：返回值对象）
    // ============================================

    public String getTaskId() { return taskId.getValue(); }
    public TaskId getTaskIdVO() { return taskId; }
    
    public String getPlanId() { return planId.getValue(); }
    public PlanId getPlanIdVO() { return planId; }
    
    public String getTenantId() { return tenantId.getValue(); }
    public TenantId getTenantIdVO() { return tenantId; }

    public Long getDeployUnitId() { 
        return deployVersion != null ? deployVersion.getDeployUnitId() : null; 
    }
    public void setDeployUnitId(Long deployUnitId) { 
        if (deployVersion == null) {
            this.deployVersion = DeployVersion.of(deployUnitId, null);
        } else {
            this.deployVersion = DeployVersion.of(deployUnitId, deployVersion.getDeployUnitVersion());
        }
    }
    
    public Long getDeployUnitVersion() { 
        return deployVersion != null ? deployVersion.getDeployUnitVersion() : null; 
    }
    public void setDeployUnitVersion(Long deployUnitVersion) { 
        if (deployVersion == null) {
            this.deployVersion = DeployVersion.of(null, deployUnitVersion);
        } else {
            this.deployVersion = DeployVersion.of(deployVersion.getDeployUnitId(), deployUnitVersion);
        }
    }
    
    public DeployVersion getDeployVersion() { return deployVersion; }
    public void setDeployVersion(DeployVersion deployVersion) { this.deployVersion = deployVersion; }
    
    public String getDeployUnitName() { return deployUnitName; }
    public void setDeployUnitName(String deployUnitName) { this.deployUnitName = deployUnitName; }

    public TaskStatus getStatus() { return status; }

    public int getCurrentStageIndex() { 
        return stageProgress != null ? stageProgress.getCurrentStageIndex() : 0; 
    }

    public int getRetryCount() { 
        return retryPolicy != null ? retryPolicy.getRetryCount() : 0; 
    }

    public Integer getMaxRetry() { 
        return retryPolicy != null ? retryPolicy.getMaxRetry() : null; 
    }
    public void setMaxRetry(Integer maxRetry) { 
        if (retryPolicy == null) {
            this.retryPolicy = RetryPolicy.initial(maxRetry);
        } else {
            this.retryPolicy = RetryPolicy.of(retryPolicy.getRetryCount(), maxRetry);
        }
    }

    public int getTotalStages() { 
        return stageProgress != null ? stageProgress.getTotalStages() : 0; 
    }
    public void setTotalStages(int totalStages) { 
        this.stageProgress = StageProgress.initial(totalStages);
        this.retryPolicy = RetryPolicy.initial(null);
    }

    public TaskCheckpoint getCheckpoint() { return checkpoint; }
    public void setCheckpoint(TaskCheckpoint checkpoint) { this.checkpoint = checkpoint; }

    public List<StageResult> getStageResults() { return stageResults; }

    public LocalDateTime getCreatedAt() { return timeRange != null ? timeRange.getCreatedAt() : null; }
    public LocalDateTime getStartedAt() { return timeRange != null ? timeRange.getStartedAt() : null; }
    public LocalDateTime getEndedAt() { return timeRange != null ? timeRange.getEndedAt() : null; }

    public TenantDeployConfigSnapshot getPrevConfigSnapshot() { return prevConfigSnapshot; }
    public void setPrevConfigSnapshot(TenantDeployConfigSnapshot prevConfigSnapshot) {
        this.prevConfigSnapshot = prevConfigSnapshot;
    }

    public Long getLastKnownGoodVersion() { return lastKnownGoodVersion; }
    public void setLastKnownGoodVersion(Long lastKnownGoodVersion) {
        this.lastKnownGoodVersion = lastKnownGoodVersion;
    }

    public Long getDurationMillis() { 
        return duration != null ? duration.getDurationMillis() : null; 
    }

    public boolean isPauseRequested() { return pauseRequested; }
    public String getCancelledBy() { return cancelledBy; }
    
    // RF-13: 值对象访问方法
    public StageProgress getStageProgress() { return stageProgress; }
    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public TimeRange getTimeRange() { return timeRange; }
    public TaskDuration getDuration() { return duration; }
}




