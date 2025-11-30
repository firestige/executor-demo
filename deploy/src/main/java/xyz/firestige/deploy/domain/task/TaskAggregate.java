package xyz.firestige.deploy.domain.task;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.DeployVersion;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.shared.vo.TimeRange;
import xyz.firestige.deploy.domain.task.event.TaskStageFailedEvent;
import xyz.firestige.deploy.domain.task.event.TaskStageStartedEvent;
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
import xyz.firestige.deploy.domain.task.event.TaskStageCompletedEvent;
import xyz.firestige.deploy.domain.task.event.TaskStartedEvent;
import xyz.firestige.deploy.domain.task.event.TaskStatusEvent;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

import java.time.Duration;
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

    /**
     * 执行范围（T-034）
     * <p>
     * 定义本次执行的 Stage 范围 [startIndex, endIndex)
     * - 正常执行：[0, totalStages)
     * - 重试执行：[checkpoint+1, totalStages)
     * - 回滚执行：[0, checkpoint+2)
     *
     * @since T-034 分离执行范围和执行进度
     */
    private ExecutionRange executionRange;

    private RetryPolicy retryPolicy;

    private TaskCheckpoint checkpoint;
    private List<StageResult> stageResults = new ArrayList<>();

    private TimeRange timeRange;

    private TenantDeployConfigSnapshot prevConfigSnapshot; // 上一次可用配置快照
    private Long lastKnownGoodVersion; // 上一次成功切换完成的版本号
    private TaskDuration duration;

    // T-028: 保存完整的上一版配置（用于回滚重新装配 Stage）
    private TenantConfig prevConfig;

    // 运行时标志
    private boolean pauseRequested;
    private String cancelledBy;

    /**
     * 回滚意图标志
     * <p>
     * 用于标识当前任务是否处于回滚模式：
     * - true: 任务使用旧配置重新执行（回滚）
     * - false: 任务使用新配置正常执行
     * <p>
     * 设计说明：
     * - 内部状态机不区分回滚/正常执行（都是 PENDING→RUNNING→COMPLETED）
     * - 只在发布领域事件时根据此标志选择事件类型
     * - 外部通过事件（TaskRollbackStarted/TaskRollbackCompleted）观测回滚
     *
     * @since T-032 状态机简化
     */
    private boolean rollbackIntent = false;

    // ============================================
    // RF-11: 领域事件收集
    // ============================================
    private final List<TaskStatusEvent> domainEvents = new ArrayList<>();

    public TaskAggregate(TaskId taskId, PlanId planId, TenantId tenantId) {
        this.taskId = taskId;
        this.planId = planId;
        this.tenantId = tenantId;
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
     * <p>
     * 事件发布：
     * - 回滚意图：发布 TaskRollbackStarted
     * - 正常执行：发布 TaskStarted
     */
    public void start() {
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException(
                String.format("只有 PENDING 状态的任务可以启动，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }
        this.status = TaskStatus.RUNNING;
        this.timeRange = timeRange.start();

        // ✅ 根据回滚意图发布不同事件
        if (rollbackIntent) {
            // 回滚开始事件
            TaskRollingBackEvent event = new TaskRollingBackEvent(
                TaskInfo.from(this),
                "回滚执行",
                null  // stagesToRollback，回滚不需要逆序，使用 null
            );
            addDomainEvent(event);
        } else {
            // 正常启动事件
            TaskStartedEvent event = new TaskStartedEvent(
                TaskInfo.from(this),
                stageProgress.getTotalStages()
            );
            addDomainEvent(event);
        }
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
            TaskPausedEvent event = new TaskPausedEvent(TaskInfo.from(this), "system", stageProgress.getCurrentStageName());
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
        TaskResumedEvent event = new TaskResumedEvent(TaskInfo.from(this), "system", stageProgress.getCurrentStageName());
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
        TaskCancelledEvent event = new TaskCancelledEvent(TaskInfo.from(this), cancelledBy, stageProgress.getCurrentStageName());
        addDomainEvent(event);
    }

    /**
     * 开始执行 Stage（RF-19-01 新增）
     * 不变式：必须处于 RUNNING 状态
     *
     * @param stageName Stage 名称
     * @param totalSteps Stage 包含的 Step 总数
     */
    public void startStage(String stageName, int totalSteps) {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                String.format("只有 RUNNING 状态才能开始 Stage，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }

        // ✅ 产生领域事件
        TaskStageStartedEvent event =
            new TaskStageStartedEvent(
                TaskInfo.from(this),
                stageName,
                totalSteps
            );
        addDomainEvent(event);
    }

    /**
     * 完成当前 Stage（RF-18: 新方法，带进度信息）
     * 不变式：必须处于 RUNNING 状态
     *
     * @since T-032 状态机重构：移除自动 complete()，由 TaskExecutor 显式调用
     */
    public void completeStage(String stageName, Duration duration) {
        validateCanCompleteStage();

        // 推进进度
        this.stageProgress = stageProgress.next();

        // ✅ 产生领域事件（包含进度信息）
        StageResult result = StageResult.success(stageName);
        stageResults.add(result);
        result.setDuration(duration);
        TaskStageCompletedEvent event = new TaskStageCompletedEvent(TaskInfo.from(this), stageName, result);
        addDomainEvent(event);

        // ✅ T-032: 移除自动转换，由 TaskExecutor 显式调用 completeTask()
        // 不再检查 stageProgress.isCompleted() 并自动 complete()
    }

    /**
     * Stage 失败（原有方法，保持兼容）
     * 不变式：必须处于 RUNNING 状态
     */
    public void failStage(StageResult result) {
        validateCanCompleteStage();

        this.stageResults.add(result);
        this.status = TaskStatus.FAILED;
        this.timeRange = timeRange.end();
        calculateDuration();

        // ✅ 产生领域事件
        List<String> completedStages = stageResults.stream().map(StageResult::getStageName).toList();
        TaskFailedEvent event = new TaskFailedEvent(TaskInfo.from(this), result.getFailureInfo(), completedStages, result.getStageName());
        addDomainEvent(event);
    }

    /**
     * Stage 失败（RF-19-01 新增：专门产生 TaskStageFailedEvent）
     * 不变式：必须处于 RUNNING 状态
     *
     * @param stageName 失败的 Stage 名称
     * @param failureInfo 失败信息
     */
    public void failStage(String stageName, FailureInfo failureInfo) {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                String.format("只有 RUNNING 状态才能记录 Stage 失败，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }

        // 业务逻辑：记录失败的 Stage（但不改变 Task 状态，由外部决定）
        StageResult result = StageResult.failure(stageName, failureInfo);
        this.stageResults.add(result);

        // ✅ 产生领域事件：TaskStageFailedEvent
        TaskStageFailedEvent event =
            new TaskStageFailedEvent(
                TaskInfo.from(this),
                stageName,
                failureInfo
            );
        addDomainEvent(event);
    }

    /**
     * 完成任务（RF-18: 改为 public，支持外部调用）
     * <p>
     * 事件发布：
     * - 回滚意图：发布 TaskRolledBack（回滚完成）
     * - 正常执行：发布 TaskCompleted
     */
    public void complete() {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                String.format("只有 RUNNING 状态才能完成，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }

        // T-034: 使用 isExecutionCompleted() 判断（考虑执行范围）
        if (!isExecutionCompleted()) {
            throw new IllegalStateException(
                String.format("还有未完成的 Stage，无法完成任务，taskId: %s", taskId.getValue())
            );
        }

        this.status = TaskStatus.COMPLETED;
        this.timeRange = timeRange.end();
        calculateDuration();

        // ✅ 根据回滚意图发布不同事件
        List<String> completedStages = stageResults.stream().map(StageResult::getStageName).toList();

        if (rollbackIntent) {
            // 回滚完成事件
            TaskRolledBackEvent event = new TaskRolledBackEvent(
                TaskInfo.from(this),
                completedStages
            );
            addDomainEvent(event);

            // ✅ 回滚完成后清除标志
            this.rollbackIntent = false;
        } else {
            // 正常完成事件
            TaskCompletedEvent event = new TaskCompletedEvent(
                TaskInfo.from(this),
                duration.toDuration(),
                completedStages
            );
            addDomainEvent(event);
        }
    }

    /**
     * 重试任务
     * 不变式：只有 FAILED 状态可以重试
     */
    public void retry(boolean fromCheckpoint, Integer globalMaxRetry) {
        if (status != TaskStatus.FAILED) {
            throw new IllegalStateException(
                String.format("只有 FAILED 状态可以重试，当前状态: %s, taskId: %s", status, taskId.getValue())
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
        TaskRetryStartedEvent event = new TaskRetryStartedEvent(TaskInfo.from(this), fromCheckpoint);
        addDomainEvent(event);
    }

    /**
     * 重试任务（RF-18: 新方法，无参数简化版）
     * <p>
     * T-032: 重试先转到 PENDING 状态，然后由 ExecutionPreparer 再调用 startTask()
     * <p>
     * 不变式：只有 FAILED 状态可以重试
     */
    public void retry() {
        if (status != TaskStatus.FAILED) {
            throw new IllegalStateException(
                String.format("只有 FAILED 状态可以重试，当前状态: %s, taskId: %s", status, taskId.getValue())
            );
        }

        // 重置进度和重试计数
        if (retryPolicy != null) {
            this.retryPolicy = retryPolicy.incrementRetryCount();
        }

        if (stageProgress != null) {
            this.stageProgress = stageProgress.reset();
        }

        // ✅ T-032: 先转到 PENDING 状态（待执行），而不是直接 RUNNING
        this.status = TaskStatus.PENDING;

        // ✅ 产生领域事件
        TaskRetryStartedEvent event = new TaskRetryStartedEvent(TaskInfo.from(this), false);
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
        List<String> completedStages = stageResults.stream().map(StageResult::getStageName).toList();
        TaskFailedEvent event = new TaskFailedEvent(TaskInfo.from(this), failure, completedStages, stageProgress.getCurrentStageName());
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

    public TaskId getTaskId() { return taskId; }
    public TaskId getTaskIdVO() { return taskId; }
    
    public PlanId getPlanId() { return planId; }
    public PlanId getPlanIdVO() { return planId; }
    
    public TenantId getTenantId() { return tenantId; }
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

    public StageProgress getStageProgress() { return stageProgress; }

    /**
     * 获取执行范围
     *
     * @return ExecutionRange 实例
     * @since T-034 分离执行范围和执行进度
     */
    public ExecutionRange getExecutionRange() { return executionRange; }

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

    public void setTotalStages(List<TaskStage> stages) {
        this.stageProgress = StageProgress.initial(stages);
        this.retryPolicy = RetryPolicy.initial(null);
        // T-034: 初始化执行范围（默认为完整范围）
        this.executionRange = ExecutionRange.full(stages.size());
    }

    public TaskCheckpoint getCheckpoint() { return checkpoint; }

    /**
     * 记录检查点（在 Stage 左边界）
     * <p>
     * 业务规则：
     * 1. 只有 RUNNING 状态才能记录检查点
     * 2. 一个 Task 最多保留 1 个检查点（覆盖旧的）
     * 3. 检查点记录当前已完成的 Stage 列表和索引
     * <p>
     * T-033: 保存所有 Stage 名称，用于重建 StageProgress
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
        
        // ✅ 获取所有 Stage 名称（用于重建 StageProgress）
        List<String> allStageNames = stageProgress != null
            ? stageProgress.getStageNames()
            : Collections.emptyList();

        // 创建新的检查点（覆盖旧的）
        this.checkpoint = new TaskCheckpoint(lastCompletedIndex, completedStageNames, allStageNames);
    }
    
    /**
     * 恢复到检查点
     * <p>
     * T-033: 移除状态检查，这是辅助方法，只设置字段不改变状态
     * 调用方（ExecutionPreparer）负责在正确的时机调用
     * <p>
     * 关键：从检查点重建 StageProgress（支持重启后恢复）
     *
     * @param checkpoint 要恢复的检查点
     */
    public void restoreFromCheckpoint(TaskCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("检查点不能为空");
        }
        
        this.checkpoint = checkpoint;

        // ✅ 从检查点重建 StageProgress（委托给 StageProgress 工厂方法）
        this.stageProgress = StageProgress.of(checkpoint);

        // 注意：不改变 status，由 TaskExecutor 统一驱动状态转换
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

    // ============================================
    // T-034: 执行范围准备方法
    // ============================================

    /**
     * 准备回滚执行范围（使用内部 checkpoint）
     * <p>
     * 回滚时只执行已改变的 Stage：[0, checkpoint+2)
     * <p>
     * 例如：checkpoint.lastCompletedIndex=0，执行范围=[0,2)，即 stage-1 和 stage-2
     *
     * @since T-034 分离执行范围和执行进度
     */
    public void prepareRollbackRange() {
        if (this.checkpoint == null) {
            throw new IllegalStateException("无检查点，无法准备回滚");
        }
        this.executionRange = ExecutionRange.forRollback(checkpoint);
        this.stageProgress = stageProgress.reset();  // 重置进度到 0
    }

    /**
     * 准备回滚执行范围（支持外部传入 checkpoint）
     * <p>
     * 用于无状态重建场景
     *
     * @param checkpoint 检查点对象
     * @since T-034 分离执行范围和执行进度
     */
    public void prepareRollbackRange(TaskCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint 不能为空");
        }
        this.executionRange = ExecutionRange.forRollback(checkpoint);
        // 从检查点重建 StageProgress，然后重置到 0
        this.stageProgress = StageProgress.of(checkpoint).reset();
    }

    /**
     * 准备重试执行范围
     * <p>
     * 重试时从检查点后开始执行：[checkpoint+1, totalStages)
     *
     * @param checkpoint 检查点对象
     * @since T-034 分离执行范围和执行进度
     */
    public void prepareRetryRange(TaskCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint 不能为空");
        }
        this.executionRange = ExecutionRange.forRetry(checkpoint);
        // 从检查点恢复进度（currentIndex = checkpoint+1）
        this.stageProgress = StageProgress.of(checkpoint);
    }

    /**
     * 判断执行范围是否完成
     * <p>
     * 用于 complete() 方法判断是否所有需要执行的 Stage 都完成了
     *
     * @return true = 已完成，false = 未完成
     * @since T-034 分离执行范围和执行进度
     */
    public boolean isExecutionCompleted() {
        if (stageProgress == null || executionRange == null) {
            return false;
        }
        int currentIndex = stageProgress.getCurrentStageIndex();
        int totalStages = stageProgress.getTotalStages();
        int effectiveEnd = executionRange.getEffectiveEndIndex(totalStages);
        return currentIndex >= effectiveEnd;
    }

    /**
     * 获取进度视图（用于发布事件）
     *
     * @return TaskProgressView 实例
     * @since T-034 分离执行范围和执行进度
     */
    public TaskProgressView getProgressView() {
        if (stageProgress == null || executionRange == null) {
            throw new IllegalStateException("stageProgress 或 executionRange 未初始化");
        }
        return TaskProgressView.from(stageProgress, executionRange);
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
    
    /**
     * 标记任务为回滚意图
     * <p>
     * 在准备回滚执行时调用，表明接下来的执行是回滚操作
     */
    public void markAsRollbackIntent() {
        this.rollbackIntent = true;
    }

    /**
     * 清除回滚意图标志
     * <p>
     * 在回滚完成或重置时调用
     */
    public void clearRollbackIntent() {
        this.rollbackIntent = false;
    }

    /**
     * 检查是否处于回滚意图模式
     */
    public boolean isRollbackIntent() {
        return rollbackIntent;
    }

    /**
     * 获取 Stage 总数
     * <p>
     * 用于 CheckpointService 验证是否是最后一个 Stage
     *
     * @return Stage 总数
     * @since T-032 状态机重构
     */
    public int getTotalStages() {
        return stageProgress != null ? stageProgress.getTotalStages() : 0;
    }

    // RF-13: 值对象访问方法
    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public TimeRange getTimeRange() { return timeRange; }
    public TaskDuration getDuration() { return duration; }

    public TenantConfig getPrevConfig() {
        return prevConfig;
    }

    public void setPrevConfig(TenantConfig prevConfig) {
        this.prevConfig = prevConfig;
    }

    public FailureInfo getFailureInfo() {
        return null;
    }
}
