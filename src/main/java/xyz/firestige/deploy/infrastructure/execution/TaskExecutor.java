package xyz.firestige.deploy.infrastructure.execution;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import xyz.firestige.deploy.application.checkpoint.CheckpointService;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.StateTransitionService;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.metrics.MetricsRegistry;
import xyz.firestige.deploy.infrastructure.metrics.NoopMetricsRegistry;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;

/**
 * TaskExecutor（RF-18: 基于方案C的事件驱动架构重构）
 * 
 * <p>核心架构：
 * <pre>
 * TaskExecutor (Infrastructure Layer)
 *     ↓ 调用
 * StateTransitionService.canTransition()  // ✅ 低成本前置检查（内存操作）
 *     ↓ 检查通过
 * TaskDomainService.startTask()           // ✅ 高成本操作（DB + 事件）
 *     ↓ 内部调用
 * TaskAggregate.start()                   // ✅ 业务逻辑 + 事件产生
 * </pre>
 * 
 * <p>职责：
 * <ul>
 *   <li>编排 Stage 执行流程</li>
 *   <li>通过 StateTransitionService 进行低成本前置检查</li>
 *   <li>通过 TaskDomainService 执行高成本状态转换</li>
 *   <li>管理 HeartbeatScheduler 心跳</li>
 *   <li>处理 Checkpoint 保存/恢复</li>
 *   <li>处理租户冲突管理</li>
 * </ul>
 * 
 * @since RF-18: 事件驱动架构重构
 */
public class TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    private final PlanId planId;
    private final TaskAggregate task;
    private final List<TaskStage> stages;
    private final TaskRuntimeContext context;

    // ✅ RF-18: 核心依赖
    private final TaskDomainService taskDomainService;
    private final StateTransitionService stateTransitionService;

    // 基础设施依赖
    private final ApplicationEventPublisher technicalEventPublisher;
    private final CheckpointService checkpointService;
    private final TenantConflictManager conflictManager;
    private final MetricsRegistry metrics;

    // 心跳调度器
    private final int progressIntervalSeconds;
    private volatile HeartbeatScheduler heartbeatScheduler;

    /**
     * RF-18: 新构造函数（完整依赖）
     */
    public TaskExecutor(
            PlanId planId,
            TaskAggregate task,
            List<TaskStage> stages,
            TaskRuntimeContext context,
            TaskDomainService taskDomainService,
            StateTransitionService stateTransitionService,
            ApplicationEventPublisher technicalEventPublisher,
            CheckpointService checkpointService,
            TenantConflictManager conflictManager,
            int progressIntervalSeconds,
            MetricsRegistry metrics) {
        this.planId = planId;
        this.task = task;
        this.stages = stages != null ? stages : new ArrayList<>();
        this.context = context;
        this.taskDomainService = taskDomainService;
        this.stateTransitionService = stateTransitionService;
        this.technicalEventPublisher = technicalEventPublisher;
        this.checkpointService = checkpointService;
        this.conflictManager = conflictManager;
        this.progressIntervalSeconds = progressIntervalSeconds <= 0 ? 10 : progressIntervalSeconds;
        this.metrics = metrics != null ? metrics : new NoopMetricsRegistry();
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
     * RF-18: 执行任务（基于方案C架构）
     */
    public TaskResult execute() {
        TaskId taskId = task.getTaskId();
        LocalDateTime startTime = LocalDateTime.now();
        List<StageResult> completedStages = new ArrayList<>();
        
        try {
            // 注入 MDC
            context.injectMdc(null);
            metrics.incrementCounter("task_active");
            
            // 1. ✅ 前置检查：是否可以启动/恢复
            TaskStatus currentStatus = task.getStatus();
            TaskStatus targetStatus = TaskStatus.RUNNING;
            
            if (!stateTransitionService.canTransition(task, targetStatus, context)) {
                log.error("状态转换不允许: {} -> {}, taskId: {}", currentStatus, targetStatus, taskId);
                return TaskResult.fail(
                    planId,
                    taskId,
                    currentStatus,
                    "状态转换验证失败: " + currentStatus + " -> " + targetStatus,
                    Duration.between(startTime, LocalDateTime.now()),
                    completedStages
                );
            }
            
            // 2. ✅ 通过检查后才执行高成本操作
            if (currentStatus == TaskStatus.PAUSED) {
                taskDomainService.resumeTask(task, context);
                log.info("任务恢复执行, taskId: {}", taskId);
            } else if (currentStatus == TaskStatus.PENDING) {
                taskDomainService.startTask(task, context);
                log.info("任务开始执行, taskId: {}", taskId);
            } else {
                log.warn("任务状态异常: {}, 尝试继续执行, taskId: {}", currentStatus, taskId);
            }
            
            // 3. 启动心跳
            startHeartbeat();
            
            // 4. 从检查点恢复
            var checkpoint = checkpointService.loadCheckpoint(task);
            int startIndex = (checkpoint != null) ? checkpoint.getLastCompletedStageIndex() + 1 : 0;
            log.info("从 Stage 索引 {} 开始执行, taskId: {}", startIndex, taskId);
            
            // 5. 执行 Stages
            for (int i = startIndex; i < stages.size(); i++) {
                TaskStage stage = stages.get(i);
                String stageName = stage.getName();
                
                // 执行 Stage
                log.info("开始执行 Stage: {}, taskId: {}", stageName, taskId);
                context.injectMdc(stageName);
                
                StageResult stageResult = stage.execute(context);
                
                if (stageResult.isSuccess()) {
                    // ✅ Stage 成功
                    Duration duration =stageResult.getDuration();
                    taskDomainService.completeStage(task, stageName, duration, context);
                    
                    completedStages.add(stageResult);
                    checkpointService.saveCheckpoint(task, extractStageNames(completedStages), i);
                    
                    log.info("Stage 执行成功: {}, 耗时: {}ms, taskId: {}", 
                        stageName, stageResult.getDuration().toMillis(), taskId);
                } else {
                    // ✅ Stage 失败：前置检查
                    log.error("Stage 执行失败: {}, 原因: {}, taskId: {}", 
                        stageName, stageResult.getFailureInfo().getErrorMessage(), taskId);
                    
                    // RF-19: 前置检查状态转换
                    if (stateTransitionService.canTransition(task, TaskStatus.FAILED, context)) {
                        taskDomainService.failTask(task, stageResult.getFailureInfo(), context);
                        log.info("任务状态已更新为 FAILED, taskId: {}", taskId);
                    } else {
                        log.warn("当前状态不允许转换为 FAILED: {}, taskId: {}", task.getStatus(), taskId);
                    }
                    
                    stopHeartbeat();
                    releaseTenantLock();
                    metrics.incrementCounter("task_failed");
                    
                    return TaskResult.fail(
                        planId,
                        taskId,
                        task.getStatus(),
                        stageResult.getFailureInfo().getErrorMessage(),
                        Duration.between(startTime, LocalDateTime.now()),
                        completedStages
                    );
                }
                
                // 检查暂停请求
                if (context.isPauseRequested()) {
                    // RF-19: 前置检查状态转换
                    if (stateTransitionService.canTransition(task, TaskStatus.PAUSED, context)) {
                        taskDomainService.pauseTask(task, context);
                        log.info("任务暂停, taskId: {}", taskId);
                        
                        stopHeartbeat();
                        metrics.incrementCounter("task_paused");
                        
                        return TaskResult.ok(
                            planId,
                            taskId,
                            task.getStatus(),
                            Duration.between(startTime, LocalDateTime.now()),
                            completedStages
                        );
                    } else {
                        log.warn("收到暂停请求但当前状态不允许暂停: {}, taskId: {}", task.getStatus(), taskId);
                        // 继续执行，不暂停
                    }
                }
                
                // 检查取消请求
                if (context.isCancelRequested()) {
                    // RF-19: 前置检查状态转换
                    if (stateTransitionService.canTransition(task, TaskStatus.CANCELLED, context)) {
                        taskDomainService.cancelTask(task, "用户取消", context);
                        log.info("任务取消, taskId: {}", taskId);
                        
                        stopHeartbeat();
                        releaseTenantLock();
                        metrics.incrementCounter("task_cancelled");
                        
                        return TaskResult.ok(
                            planId,
                            taskId,
                            task.getStatus(),
                            Duration.between(startTime, LocalDateTime.now()),
                            completedStages
                        );
                    } else {
                        log.warn("收到取消请求但当前状态不允许取消: {}, taskId: {}", task.getStatus(), taskId);
                        // 继续执行，不取消
                    }
                }
            }
            
            // 6. 完成任务
            // RF-19: 前置检查状态转换
            if (stateTransitionService.canTransition(task, TaskStatus.COMPLETED, context)) {
                taskDomainService.completeTask(task, context);
                log.info("任务完成, taskId: {}", taskId);
            } else {
                log.warn("所有 Stage 已完成但当前状态不允许转换为 COMPLETED: {}, taskId: {}", 
                    task.getStatus(), taskId);
            }
            
            stopHeartbeat();
            releaseTenantLock();
            checkpointService.clearCheckpoint(task);
            metrics.incrementCounter("task_completed");
            
            return TaskResult.ok(
                planId,
                taskId,
                task.getStatus(),
                Duration.between(startTime, LocalDateTime.now()),
                completedStages
            );
            
        } catch (Exception e) {
            log.error("任务执行异常, taskId: {}, error: {}", taskId, e.getMessage(), e);
            
            // RF-19: 异常处理也需前置检查
            if (stateTransitionService.canTransition(task, TaskStatus.FAILED, context)) {
                FailureInfo failure = FailureInfo.of(ErrorType.BUSINESS_ERROR , e.getMessage());
                taskDomainService.failTask(task, failure, context);
                log.info("任务因异常失败, taskId: {}", taskId);
            } else {
                log.warn("任务执行异常但当前状态不允许转换为 FAILED: {}, taskId: {}", 
                    task.getStatus(), taskId);
            }
            
            stopHeartbeat();
            releaseTenantLock();
            metrics.incrementCounter("task_failed");
            
            return TaskResult.fail(
                planId,
                taskId,
                task.getStatus(),
                e.getMessage(),
                Duration.between(startTime, LocalDateTime.now()),
                completedStages
            );
        } finally {
            context.clearMdc();
        }
    }

    /**
     * 启动心跳调度器
     */
    private void startHeartbeat() {
        if (heartbeatScheduler == null) {
            // 创建心跳调度器
            heartbeatScheduler = new HeartbeatScheduler(
                task,
                technicalEventPublisher,
                progressIntervalSeconds,
                metrics
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
        if (conflictManager != null) {
            conflictManager.releaseTask(task.getTenantId());
            log.debug("租户锁已释放, tenantId: {}", task.getTenantId());
        }
    }

    /**
     * 提取 Stage 名称列表
     */
    private List<String> extractStageNames(List<StageResult> results) {
        List<String> names = new ArrayList<>();
        for (StageResult r : results) {
            names.add(r.getStageName());
        }
        return names;
    }

    public int getCompletedStageCount() {
        return task.getCurrentStageIndex();
    }

    /**
     * RF-18: 回滚执行（基于方案C架构）
     * 
     * <p>流程：
     * <ol>
     *   <li>前置检查：canTransition(ROLLING_BACK)</li>
     *   <li>开始回滚：taskDomainService.startRollback()</li>
     *   <li>逆序执行各 Stage 的 rollback</li>
     *   <li>完成回滚：taskDomainService.completeRollback() 或 failRollback()</li>
     * </ol>
     */
    public TaskResult rollback() {
        TaskId taskId = task.getTaskId();
        LocalDateTime startTime = LocalDateTime.now();
        List<StageResult> rollbackStages = new ArrayList<>();
        
        try {
            // 1. ✅ 前置检查：是否可以回滚
            if (!stateTransitionService.canTransition(task, TaskStatus.ROLLING_BACK, context)) {
                log.error("状态转换不允许回滚: {}, taskId: {}", task.getStatus(), taskId);
                return TaskResult.fail(
                    planId,
                    taskId,
                    task.getStatus(),
                    "当前状态不允许回滚: " + task.getStatus(),
                    Duration.between(startTime, LocalDateTime.now()),
                    rollbackStages
                );
            }
            
            // 2. ✅ 开始回滚
            taskDomainService.startRollback(task, context);
            log.info("开始回滚任务, taskId: {}", taskId);
            
            // 3. 逆序执行各 Stage 的 rollback
            List<TaskStage> reversedStages = new ArrayList<>(stages);
            java.util.Collections.reverse(reversedStages);
            
            boolean anyFailed = false;
            for (TaskStage stage : reversedStages) {
                String stageName = stage.getName();
                log.info("回滚 Stage: {}, taskId: {}", stageName, taskId);
                
                StageResult stageResult = new StageResult(stageName);
                
                try {
                    // 调用 Stage 的回滚方法
                    stage.rollback(context);
                    stageResult.setSuccess(true);
                    log.info("Stage 回滚成功: {}, taskId: {}", stageName, taskId);
                } catch (Exception ex) {
                    stageResult.setSuccess(false);
                    anyFailed = true;
                    log.error("Stage 回滚失败: {}, taskId: {}, error: {}", 
                        stageName, taskId, ex.getMessage(), ex);
                }
                
                rollbackStages.add(stageResult);
            }
            
            // 4. ✅ 完成回滚或标记失败
            if (anyFailed) {
                // RF-19: 前置检查状态转换
                if (stateTransitionService.canTransition(task, TaskStatus.ROLLBACK_FAILED, context)) {
                    FailureInfo failure = FailureInfo.of(ErrorType.BUSINESS_ERROR, "部分 Stage 回滚失败");
                    taskDomainService.failRollback(task, failure, context);
                    log.info("回滚失败，状态已更新, taskId: {}", taskId);
                } else {
                    log.warn("回滚失败但当前状态不允许转换为 ROLLBACK_FAILED: {}, taskId: {}", 
                        task.getStatus(), taskId);
                }
                log.error("回滚失败, taskId: {}", taskId);
            } else {
                // RF-19: 前置检查状态转换
                if (stateTransitionService.canTransition(task, TaskStatus.ROLLED_BACK, context)) {
                    taskDomainService.completeRollback(task, context);
                    log.info("回滚成功，状态已更新, taskId: {}", taskId);
                } else {
                    log.warn("回滚成功但当前状态不允许转换为 ROLLED_BACK: {}, taskId: {}", 
                        task.getStatus(), taskId);
                }
                log.info("回滚成功, taskId: {}", taskId);
            }
            
            releaseTenantLock();
            metrics.incrementCounter("rollback_count");
            
            return TaskResult.ok(
                planId,
                taskId,
                task.getStatus(),
                Duration.between(startTime, LocalDateTime.now()),
                rollbackStages
            );
            
        } catch (Exception e) {
            log.error("回滚异常, taskId: {}, error: {}", taskId, e.getMessage(), e);
            
            // RF-19: 异常时的状态转换检查
            if (stateTransitionService.canTransition(task, TaskStatus.ROLLBACK_FAILED, context)) {
                FailureInfo failure = FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage());
                taskDomainService.failRollback(task, failure, context);
                log.info("回滚异常，状态已更新为 ROLLBACK_FAILED, taskId: {}", taskId);
            } else {
                log.warn("回滚异常但当前状态不允许转换为 ROLLBACK_FAILED: {}, taskId: {}", 
                    task.getStatus(), taskId);
            }
            
            releaseTenantLock();
            metrics.incrementCounter("rollback_failed");
            
            return TaskResult.fail(
                planId,
                taskId,
                task.getStatus(),
                e.getMessage(),
                Duration.between(startTime, LocalDateTime.now()),
                rollbackStages
            );
        }
    }

    /**
     * RF-18: 重试任务（基于方案C架构）
     * 
     * <p>流程：
     * <ol>
     *   <li>前置检查：canTransition(RUNNING)</li>
     *   <li>执行重试：taskDomainService.retryTask()</li>
     *   <li>清理检查点（如果需要）</li>
     *   <li>重新执行：execute()</li>
     * </ol>
     * 
     * @param fromCheckpoint 是否从检查点重试
     * @return 执行结果
     */
    public TaskResult retry(boolean fromCheckpoint) {
        TaskId taskId = task.getTaskId();
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            log.info("开始重试任务, fromCheckpoint: {}, taskId: {}", fromCheckpoint, taskId);
            
            // 1. ✅ 前置检查：是否可以重试
            if (!stateTransitionService.canTransition(task, TaskStatus.RUNNING, context)) {
                log.error("状态转换不允许重试: {}, taskId: {}", task.getStatus(), taskId);
                return TaskResult.fail(
                    planId,
                    taskId,
                    task.getStatus(),
                    "当前状态不允许重试: " + task.getStatus(),
                    Duration.between(startTime, LocalDateTime.now()),
                    new ArrayList<>()
                );
            }
            
            // 2. ✅ 执行重试（会重置进度和状态）
            taskDomainService.retryTask(task, context);
            log.info("任务重试状态已更新, taskId: {}", taskId);
            
            // 3. 清理检查点（如果不从检查点重试）
            if (!fromCheckpoint) {
                checkpointService.clearCheckpoint(task);
                log.info("检查点已清除, taskId: {}", taskId);
            }
            
            // 4. 停止旧的心跳（如果存在）
            stopHeartbeat();
            
            // 5. 重新执行任务
            log.info("重新执行任务, taskId: {}", taskId);
            TaskResult result = execute();
            
            log.info("重试完成, taskId: {}", taskId);
            return result;
            
        } catch (Exception e) {
            log.error("重试异常, taskId: {}, error: {}", taskId, e.getMessage(), e);
            
            return TaskResult.fail(
                planId,
                taskId,
                task.getStatus(),
                "重试失败: " + e.getMessage(),
                Duration.between(startTime, LocalDateTime.now()),
                new ArrayList<>()
            );
        }
    }

    /**
     * 调用回滚（对外接口）
     */
    public TaskResult invokeRollback() {
        return rollback();
    }
}
