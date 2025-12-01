package xyz.firestige.deploy.infrastructure.execution;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.ExecutionRange;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.execution.strategy.ExecutionDependencies;
import xyz.firestige.deploy.infrastructure.execution.strategy.ExecutionPreparer;

/**
 * TaskExecutor（T-032 优化版：准备器模式）
 *
 * <p>核心架构：
 * <pre>
 * TaskExecutor (Infrastructure Layer)
 *     ↓
 * ExecutionPreparer.prepare()             // ✅ 准备阶段：状态转换 + 确定起点
 *     ↓
 * executeNormalStages() or executeRollback()  // ✅ 执行阶段：统一的 Stage 循环
 * </pre>
 * 
 * <p>职责：
 * <ul>
 *   <li>统一的执行入口（execute）</li>
 *   <li>委托给 ExecutionPreparer 完成准备工作</li>
 *   <li>根据 Context 的执行信息选择执行路径</li>
 *   <li>管理 HeartbeatScheduler 心跳</li>
 *   <li>资源清理</li>
 * </ul>
 *
 * <p>设计理念：
 * <ul>
 *   <li>准备与执行分离</li>
 *   <li>统一的执行入口（无 retry/rollback 方法）</li>
 *   <li>通过 Context 标志位驱动</li>
 * </ul>
 * 
 * @since T-032: 准备器模式重构
 */
public class TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    private final PlanId planId;
    private final TaskAggregate task;
    private final List<TaskStage> stages;
    private final TaskRuntimeContext context;

    // ✅ T-032: 核心依赖
    private final ExecutionPreparer preparer;
    private final ExecutionDependencies dependencies;

    // 心跳调度器
    private final int progressIntervalSeconds;
    private volatile HeartbeatScheduler heartbeatScheduler;

    /**
     * T-032: 新构造函数（简化版）
     */
    public TaskExecutor(
            PlanId planId,
            TaskAggregate task,
            List<TaskStage> stages,
            TaskRuntimeContext context,
            ExecutionPreparer preparer,
            ExecutionDependencies dependencies,
            int progressIntervalSeconds) {
        this.planId = planId;
        this.task = task;
        this.stages = stages != null ? stages : new ArrayList<>();
        this.context = context;
        this.preparer = preparer;
        this.dependencies = dependencies;
        this.progressIntervalSeconds = progressIntervalSeconds <= 0 ? 10 : progressIntervalSeconds;
    }

    public void setHeartbeatScheduler(HeartbeatScheduler heartbeatScheduler) {
        this.heartbeatScheduler = heartbeatScheduler;
    }

    public String getCurrentStageName() {
        int idx = task.getCurrentStageIndex() - 1;
        if (idx >= 0 && idx < stages.size()) return stages.get(idx).getName();
        return null;
    }

    /**
     * T-032: 统一的执行入口
     * <p>
     * 流程：
     * 1. 准备阶段：ExecutionPreparer 完成状态转换并设置执行信息
     * 2. 执行阶段：根据 context 的执行模式选择执行路径
     * 3. 清理阶段：释放资源
     * <p>
     * 所有执行都通过此方法：
     * - 首次执行：PENDING → RUNNING
     * - 恢复执行：PAUSED → RUNNING
     * - 重试执行：FAILED → RUNNING（需先设置 context.requestRetry()）
     * - 回滚执行：FAILED → ROLLING_BACK（需先设置 context.requestRollback()）
     */
    public TaskResult execute() {
        TaskId taskId = task.getTaskId();
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // MDC 注入
            context.injectMdc(null);
            dependencies.getMetrics().incrementCounter("task_active");

            // ========== 第一步：准备执行 ==========
            log.info("开始执行任务, taskId: {}, currentStatus: {}", taskId, task.getStatus());
            preparer.prepare(task, context, dependencies);

            // 启动心跳
            startHeartbeat();

            // ========== 第二步：执行 Stages ==========
            // ✅ T-032: 回滚也使用正常模式执行，区别只是配置来源（由 Preparer 准备）
            // - 正常执行：使用新配置（currentConfig）
            // - 回滚执行：使用旧配置（prevConfigSnapshot）
            // Stage 顺序和执行逻辑完全相同
            log.info("执行 Stages, taskId: {}, startIndex: {}, mode: {}",
                taskId, context.getStartIndex(), context.getExecutionMode());

            TaskResult result = executeNormalStages(context.getStartIndex(), startTime);

            // ========== 第三步：清理 ==========
            cleanup(result);

            return result;

        } catch (Exception e) {
            log.error("任务执行异常, taskId: {}, error: {}", taskId, e.getMessage(), e);
            return handleException(e, startTime);
        } finally {
            context.clearMdc();
        }
    }

    /**
     * 执行正常 Stages（从 startIndex 开始）
     * <p>
     * ✅ T-034: 统一的执行逻辑，使用 ExecutionRange 控制范围
     * - 首次执行（range = [0, totalStages)）
     * - 重试执行（range = [checkpoint+1, totalStages)）
     * - 回滚执行（range = [0, checkpoint+2)）
     */
    private TaskResult executeNormalStages(int startIndex, LocalDateTime startTime) {
        TaskId taskId = task.getTaskId();
        List<StageResult> completedStages = new ArrayList<>();

        // T-034: 从 ExecutionRange 获取执行范围
        ExecutionRange range = task.getExecutionRange();
        int totalStages = stages.size();
        int effectiveStart = range != null ? range.getStartIndex() : startIndex;
        int effectiveEnd = range != null ? range.getEffectiveEndIndex(totalStages) : totalStages;

        log.info("开始执行 Stages, taskId: {}, range: [{}, {}), totalStages: {}",
            taskId, effectiveStart, effectiveEnd, totalStages);

        // Stage 循环：使用执行范围
        for (int i = effectiveStart; i < effectiveEnd && i < totalStages; i++) {
            TaskStage stage = stages.get(i);
            String stageName = stage.getName();
            int totalSteps = stage.getSteps().size();

            // T-034: 判断是否是执行范围内的最后一个 Stage
            boolean isLastInRange = range != null && range.isLastInRange(i, totalStages);

            // 开始 Stage
            dependencies.getTaskDomainService().startStage(task, stageName, totalSteps);
            log.info("开始执行 Stage: {}, taskId: {}", stageName, taskId);
            context.injectMdc(stageName);

            // 执行 Stage
            StageResult stageResult = stage.execute(context);

            if (stageResult.isSuccess()) {
                // Stage 成功
                handleStageSuccess(stage, stageResult, i, isLastInRange, completedStages);
            } else {
                // Stage 失败
                return handleStageFailure(stageResult, completedStages, startTime);
            }

            // 检查暂停/取消
            TaskResult pauseOrCancelResult = checkPauseOrCancel(completedStages, startTime);
            if (pauseOrCancelResult != null) {
                return pauseOrCancelResult;
            }
        }

        // 所有 Stage 完成
        return completeTask(completedStages, startTime);
    }

    /**
     * 处理 Stage 成功
     *
     * @param isLastInRange T-034: 是否是执行范围内的最后一个 Stage
     */
    private void handleStageSuccess(
        TaskStage stage,
        StageResult result,
        int stageIndex,
        boolean isLastInRange,
        List<StageResult> completedStages
    ) {
        String stageName = stage.getName();
        TaskId taskId = task.getTaskId();

        // 完成 Stage
        dependencies.getTaskDomainService().completeStage(task, stageName, result.getDuration(), context);
        completedStages.add(result);

        // T-035: 无状态执行器，移除 checkpoint 保存逻辑
        // 进度已在 StageProgress 中维护，caller 通过事件监听来持久化状态

        log.info("Stage 执行成功: {}, 耗时: {}ms, taskId: {}",
            stageName, result.getDuration().toMillis(), taskId);
    }

    /**
     * 处理 Stage 失败
     */
    private TaskResult handleStageFailure(
        StageResult result,
        List<StageResult> completedStages,
        LocalDateTime startTime
    ) {
        String stageName = result.getStageName();
        TaskId taskId = task.getTaskId();

        log.error("Stage 执行失败: {}, 原因: {}, taskId: {}",
            stageName, result.getFailureInfo().getErrorMessage(), taskId);

        // 记录 Stage 失败
        dependencies.getTaskDomainService().failStage(task, stageName, result.getFailureInfo());

        // 标记 Task 失败（T-033: 聚合根内部会检查状态）
        dependencies.getTaskDomainService().failTask(task, result.getFailureInfo(), context);
        log.info("任务状态已更新为 FAILED, taskId: {}", taskId);

        dependencies.getMetrics().incrementCounter("task_failed");

        return TaskResult.fail(
            planId, taskId, task.getStatus(),
            result.getFailureInfo().getErrorMessage(),
            Duration.between(startTime, LocalDateTime.now()),
            completedStages
        );
    }

    /**
     * 检查暂停/取消请求
     */
    private TaskResult checkPauseOrCancel(List<StageResult> completedStages, LocalDateTime startTime) {
        TaskId taskId = task.getTaskId();

        // 检查暂停
        if (context.isPauseRequested()) {
            dependencies.getTaskDomainService().pauseTask(task, context);
            log.info("任务暂停, taskId: {}", taskId);
            dependencies.getMetrics().incrementCounter("task_paused");

            return TaskResult.ok(
                planId, taskId, task.getStatus(),
                Duration.between(startTime, LocalDateTime.now()),
                completedStages
            );
        }

        // 检查取消
        if (context.isCancelRequested()) {
            dependencies.getTaskDomainService().cancelTask(task, "用户取消", context);
            log.info("任务取消, taskId: {}", taskId);
            dependencies.getMetrics().incrementCounter("task_cancelled");

            return TaskResult.ok(
                planId, taskId, task.getStatus(),
                Duration.between(startTime, LocalDateTime.now()),
                completedStages
            );
        }

        return null;  // 无暂停/取消
    }

    /**
     * 完成任务
     * <p>
     * T-033: 聚合根内部会检查状态，不需要预检验
     */
    private TaskResult completeTask(List<StageResult> completedStages, LocalDateTime startTime) {
        TaskId taskId = task.getTaskId();

        // ✅ T-033: 直接调用，聚合根内部会检查状态
        dependencies.getTaskDomainService().completeTask(task, context);
        log.info("任务完成, taskId: {}", taskId);

        dependencies.getMetrics().incrementCounter("task_completed");

        return TaskResult.ok(
            planId, taskId, task.getStatus(),
            Duration.between(startTime, LocalDateTime.now()),
            completedStages
        );
    }


    /**
     * 清理资源
     * <p>
     * T-032: FAILED 状态不清除检查点，因为重试时需要从检查点恢复
     */
    private void cleanup(TaskResult result) {
        stopHeartbeat();

        TaskStatus finalStatus = result.getFinalStatus();

        // 释放租户锁（所有终态都释放）
        if (finalStatus.isTerminal()) {
            releaseTenantLock();
        }

        // T-035: 无状态执行器，移除 checkpoint 清理逻辑
        // 不需要维护内部检查点，caller 负责状态管理
    }

    /**
     * 处理异常
     * <p>
     * T-033: 聚合根内部会检查状态，不需要预检验
     */
    private TaskResult handleException(Exception e, LocalDateTime startTime) {
        TaskId taskId = task.getTaskId();

        // 尝试标记失败（如果状态不允许，聚合根会抛异常）
        try {
            FailureInfo failure = FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage());
            dependencies.getTaskDomainService().failTask(task, failure, context);
            log.info("任务因异常失败, taskId: {}", taskId);
        } catch (IllegalStateException ex) {
            log.warn("无法标记任务失败，当前状态: {}, taskId: {}", task.getStatus(), taskId);
        }

        stopHeartbeat();
        releaseTenantLock();
        dependencies.getMetrics().incrementCounter("task_failed");

        return TaskResult.fail(
            planId, taskId, task.getStatus(), e.getMessage(),
            Duration.between(startTime, LocalDateTime.now()),
            new ArrayList<>()
        );
    }

    // ========== 辅助方法 ==========

    /**
     * 启动心跳调度器
     */
    private void startHeartbeat() {
        if (heartbeatScheduler == null) {
            // 创建心跳调度器
            heartbeatScheduler = new HeartbeatScheduler(
                task,
                dependencies.getTechnicalEventPublisher(),
                progressIntervalSeconds,
                dependencies.getMetrics()
            );
        }

        if (!heartbeatScheduler.isRunning()) {
            heartbeatScheduler.start();
            log.debug("心跳调度器已启动, taskId: {}", task.getTaskId());
        }
    }

    /**
     * 停止心跳调度器
     */
    private void stopHeartbeat() {
        if (heartbeatScheduler != null && heartbeatScheduler.isRunning()) {
            heartbeatScheduler.stop();
            log.debug("心跳调度器已停止, taskId: {}", task.getTaskId());
        }
    }

    /**
     * 释放租户锁
     */
    private void releaseTenantLock() {
        if (dependencies.getConflictManager() != null) {
            dependencies.getConflictManager().releaseTask(task.getTenantId());
            log.debug("租户锁已释放, tenantId: {}", task.getTenantId());
        }
    }

    public int getCompletedStageCount() {
        return task.getCurrentStageIndex();
    }
}
