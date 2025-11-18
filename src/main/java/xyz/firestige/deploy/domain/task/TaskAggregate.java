package xyz.firestige.deploy.domain.task;

import xyz.firestige.deploy.domain.shared.vo.DeployVersion;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.shared.vo.TimeRange;
import xyz.firestige.deploy.infrastructure.execution.StageResult;
import xyz.firestige.deploy.domain.task.event.TaskCancelledEvent;
import xyz.firestige.deploy.domain.task.event.TaskCompletedEvent;
import xyz.firestige.deploy.domain.task.event.TaskFailedEvent;
import xyz.firestige.deploy.domain.task.event.TaskPausedEvent;
import xyz.firestige.deploy.domain.task.event.TaskResumedEvent;
import xyz.firestige.deploy.domain.task.event.TaskRetryStartedEvent;
import xyz.firestige.deploy.domain.task.event.TaskRollbackFailedEvent;
import xyz.firestige.deploy.domain.task.event.TaskRolledBackEvent;
import xyz.firestige.deploy.domain.task.event.TaskRollingBackEvent;
import xyz.firestige.deploy.domain.task.event.TaskStartedEvent;
import xyz.firestige.deploy.domain.task.event.TaskStatusEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单租户 Task 聚合（DDD 重构：充血模型 + 值对象 RF-13）
 * <p>
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
     * 暂停任务（RF-18: 新方法，立即暂停）
     * 不变式：只有 RUNNING 状态可以暂停
     */
    public void pause() {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                String.format("只有 RUNNING 状态才能暂停，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }

        this.status = TaskStatus.PAUSED;
        this.pauseRequested = false;  // 清除标志

        // ✅ 产生领域事件
        TaskPausedEvent event = new TaskPausedEvent();
        event.setTaskId(taskId.getValue());
        event.setStatus(TaskStatus.PAUSED);
        addDomainEvent(event);
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
     * 完成当前 Stage（原有方法，保持兼容）
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
     * 完成当前 Stage（RF-18: 新方法，带进度信息）
     * 不变式：必须处于 RUNNING 状态
     */
    public void completeStage(String stageName, java.time.Duration duration) {
        validateCanCompleteStage();

        // 推进进度
        this.stageProgress = stageProgress.advance();

        // ✅ 产生领域事件（包含进度信息）
        xyz.firestige.deploy.domain.task.event.TaskStageCompletedEvent event = 
            new xyz.firestige.deploy.domain.task.event.TaskStageCompletedEvent(
                taskId.getValue(),
                stageName,
                stageProgress.getCurrentStageIndex(),  // 已完成的 Stage 数
                stageProgress.getTotalStages(),
                duration,
                LocalDateTime.now()
            );
        addDomainEvent(event);

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
     * 完成任务（RF-18: 改为 public，支持外部调用）
     */
    public void complete() {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                String.format("只有 RUNNING 状态才能完成，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }

        if (stageProgress != null && !stageProgress.isCompleted()) {
            throw new IllegalStateException(
                String.format("还有未完成的 Stage，无法完成任务，taskId: %s", taskId.getValue())
            );
        }

        this.status = TaskStatus.COMPLETED;
        this.timeRange = timeRange.end();
        calculateDuration();

        // ✅ 产生领域事件
        TaskCompletedEvent event = new TaskCompletedEvent();
        event.setTaskId(taskId.getValue());
        event.setStatus(TaskStatus.COMPLETED);
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
     * 重试任务（RF-18: 新方法，无参数简化版）
     * 不变式：只有 FAILED 或 ROLLED_BACK 状态可以重试
     */
    public void retry() {
        if (status != TaskStatus.FAILED && status != TaskStatus.ROLLED_BACK) {
            throw new IllegalStateException(
                String.format("只有 FAILED 或 ROLLED_BACK 状态可以重试，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }

        // 重置进度和重试计数
        if (retryPolicy != null) {
            this.retryPolicy = retryPolicy.incrementRetryCount();
        }

        if (stageProgress != null) {
            this.stageProgress = stageProgress.reset();
        }

        this.status = TaskStatus.RUNNING;

        // ✅ 产生领域事件
        TaskRetryStartedEvent event = new TaskRetryStartedEvent(taskId.getValue(), false);
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
     * 回滚任务（RF-18: 新方法，无参数简化版）
     * 不变式：终态任务无法回滚
     */
    public void rollback() {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                String.format("终态任务无法回滚，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }

        this.status = TaskStatus.ROLLING_BACK;

        // ✅ 产生领域事件
        TaskRollingBackEvent event = new TaskRollingBackEvent(taskId.getValue(), "系统触发回滚", null);
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

    /**
     * 任务失败（RF-18: 新方法，接受 FailureInfo）
     * 不变式：终态任务不能再次失败
     */
    public void fail(xyz.firestige.deploy.domain.shared.exception.FailureInfo failure) {
        if (status.isTerminal()) {
            return;
        }

        this.status = TaskStatus.FAILED;
        this.timeRange = timeRange.end();
        calculateDuration();

        // ✅ 产生领域事件
        TaskFailedEvent event = new TaskFailedEvent();
        event.setTaskId(taskId.getValue());
        event.setStatus(TaskStatus.FAILED);
        event.setMessage(failure.getErrorMessage());
        addDomainEvent(event);
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

    /**
     * 记录检查点（在 Stage 左边界）
     * <p>
     * 业务规则：
     * 1. 只有 RUNNING 状态才能记录检查点
     * 2. 一个 Task 最多保留 1 个检查点（覆盖旧的）
     * 3. 检查点记录当前已完成的 Stage 列表和索引
     * 
     * @param completedStageNames 已完成的 Stage 名称列表
     * @param lastCompletedIndex 最后完成的 Stage 索引
     */
    public void recordCheckpoint(List<String> completedStageNames, int lastCompletedIndex) {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                String.format("只有 RUNNING 状态才能记录检查点，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }
        
        if (lastCompletedIndex < 0 || lastCompletedIndex >= getTotalStages()) {
            throw new IllegalArgumentException(
                String.format("无效的 Stage 索引: %d, 总 Stage 数: %d", lastCompletedIndex, getTotalStages())
            );
        }
        
        // 创建新的检查点（覆盖旧的）
        TaskCheckpoint newCheckpoint = new TaskCheckpoint();
        newCheckpoint.getCompletedStageNames().addAll(completedStageNames);
        newCheckpoint.setLastCompletedStageIndex(lastCompletedIndex);
        newCheckpoint.setTimestamp(java.time.LocalDateTime.now());
        
        this.checkpoint = newCheckpoint;
    }
    
    /**
     * 恢复到检查点
     * <p>
     * 业务规则：
     * 1. 必须有有效的检查点
     * 2. 只能在 retry 时调用（FAILED/ROLLED_BACK 状态）
     * 
     * @param checkpoint 要恢复的检查点
     */
    public void restoreFromCheckpoint(TaskCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("检查点不能为空");
        }
        
        if (status != TaskStatus.FAILED && status != TaskStatus.ROLLED_BACK) {
            throw new IllegalStateException(
                String.format("只有 FAILED/ROLLED_BACK 状态才能恢复检查点，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }
        
        this.checkpoint = checkpoint;
        // 注意：不改变 status，由 retry() 方法负责状态转换
    }
    
    /**
     * 清除检查点
     * <p>
     * 使用场景：
     * 1. Task 完成后清理
     * 2. Task 失败且不需要恢复
     * 3. 重新开始（不从检查点恢复）
     */
    public void clearCheckpoint() {
        this.checkpoint = null;
    }

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




