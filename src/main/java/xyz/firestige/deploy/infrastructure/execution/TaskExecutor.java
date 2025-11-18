package xyz.firestige.deploy.infrastructure.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.application.checkpoint.CheckpointService;
import xyz.firestige.deploy.domain.stage.TaskStage;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.event.TaskEventSink;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.metrics.MetricsRegistry;
import xyz.firestige.deploy.infrastructure.metrics.NoopMetricsRegistry;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import xyz.firestige.deploy.domain.stage.CompositeServiceStage;
import xyz.firestige.deploy.domain.stage.rollback.RollbackStrategy;
import xyz.firestige.deploy.domain.stage.steps.ConfigUpdateStep;
import xyz.firestige.deploy.infrastructure.state.TaskStateManager;

/**
 * 新 TaskExecutor（不接线旧流程）：
 * - 注入 MDC（通过 TaskRuntimeContext 提供）
 * - 每 10 秒发布 TaskProgressEvent（由上层注入的事件下沉处理）
 * - 在 Stage 边界使用 CheckpointService 保存/恢复检查点
 */
public class TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    private final String planId;
    private final TaskAggregate task;
    private final List<TaskStage> stages;
    private final TaskRuntimeContext context;
    private final CheckpointService checkpointService;
    private final TaskEventSink eventSink;
    private final int progressIntervalSeconds;
    private volatile HeartbeatScheduler heartbeatScheduler; // 允许后置注入
    private final AtomicInteger completedCounter = new AtomicInteger();
    private final TaskStateManager stateManager; // new
    private final TenantConflictManager conflictManager; // RF-14: 统一冲突管理
    private final MetricsRegistry metrics;

    public TaskExecutor(String planId,
                        TaskAggregate task,
                        List<TaskStage> stages,
                        TaskRuntimeContext context,
                        CheckpointService checkpointService,
                        TaskEventSink eventSink,
                        int progressIntervalSeconds,
                        TaskStateManager stateManager) {
        this(planId, task, stages, context, checkpointService, eventSink, progressIntervalSeconds, stateManager, null, new NoopMetricsRegistry());
    }

    public TaskExecutor(String planId,
                        TaskAggregate task,
                        List<TaskStage> stages,
                        TaskRuntimeContext context,
                        CheckpointService checkpointService,
                        TaskEventSink eventSink,
                        int progressIntervalSeconds,
                        TaskStateManager stateManager,
                        TenantConflictManager conflictManager) {
        this(planId, task, stages, context, checkpointService, eventSink, progressIntervalSeconds, stateManager, conflictManager, new NoopMetricsRegistry());
    }

    public TaskExecutor(String planId,
                        TaskAggregate task,
                        List<TaskStage> stages,
                        TaskRuntimeContext context,
                        CheckpointService checkpointService,
                        TaskEventSink eventSink,
                        int progressIntervalSeconds,
                        TaskStateManager stateManager,
                        TenantConflictManager conflictManager,
                        MetricsRegistry metrics) {
        // ...existing assigns...
        this.planId = planId;
        this.task = task;
        this.stages = stages != null ? stages : new ArrayList<>();
        this.context = context;
        this.checkpointService = checkpointService;
        this.eventSink = eventSink;
        this.progressIntervalSeconds = progressIntervalSeconds <= 0 ? 10 : progressIntervalSeconds;
        this.stateManager = stateManager;
        this.conflictManager = conflictManager;
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

    public TaskExecutionResult execute() {
        String taskId = task.getTaskId();
        context.injectMdc(null);
        metrics.incrementCounter("task_active");
        LocalDateTime start = LocalDateTime.now();
        eventSink.publishTaskStarted(planId, taskId, stages.size(), 0);
        if (stateManager != null) {
            TaskStatus cur = stateManager.getState(taskId);
            if (cur == TaskStatus.PAUSED) {
                stateManager.updateState(taskId, TaskStatus.RESUMING);
            }
            stateManager.updateState(taskId, TaskStatus.RUNNING);
        } else {
            task.start();
        }

        List<StageResult> completed = new ArrayList<>();

        // 从检查点恢复
        var cp = checkpointService.loadCheckpoint(task);
        int startIndex = (cp != null) ? cp.getLastCompletedStageIndex() + 1 : 0;
        completedCounter.set(startIndex); // 已完成数量初始化

        if (heartbeatScheduler != null && !heartbeatScheduler.isRunning()) {
            heartbeatScheduler.start();
        }

        try {
            for (int i = startIndex; i < stages.size(); i++) {
                TaskStage s = stages.get(i);
                String stageName = s.getName();

                if (s.canSkip(context)) {
                    log.info("跳过 Stage: {}", stageName);
                    var sr = StageResult.skipped(stageName, "条件不满足，跳过执行");
                    completed.add(sr);
                    checkpointService.saveCheckpoint(task, names(completed), i);
                    completedCounter.incrementAndGet();
                    continue;
                }

                // 开始 Stage
                eventSink.publishTaskStageStarted(planId, taskId, stageName, 0);
                context.injectMdc(stageName);
                log.info("开始执行 Stage: {}", stageName);

                var stageRes = s.execute(context);
                // 将新域结果映射为旧 StageResult 以复用结构（或保留域内结果并行）
                // 这里简单标注成功/失败
                if (stageRes.isSuccess()) {
                    // 旧 StageResult 结构复用：标记 COMPLETED
                    StageResult old = new StageResult();
                    old.setStageName(stageName);
                    old.setSuccess(true);
                    completed.add(old);
                    checkpointService.saveCheckpoint(task, names(completed), i);
                    eventSink.publishTaskStageSucceeded(planId, taskId, stageName, Duration.ofMillis(stageRes.getDurationMillis()), 0);
                    completedCounter.incrementAndGet();
                    // RF-13: completeStage() 内部已更新 currentStageIndex，无需外部调用 setter

                    // If stage contains ConfigUpdateStep, update version on aggregate
                    s.getSteps().forEach(step -> {
                        if (step instanceof ConfigUpdateStep) {
                            Long v = ((ConfigUpdateStep) step).getTargetVersion();
                            if (v != null) {
                                task.setDeployUnitVersion(v);
                                task.setLastKnownGoodVersion(v); // mark as good after health check succeeds in this stage
                            }
                        }
                    });
                } else {
                    StageResult old = new StageResult();
                    old.setStageName(stageName);
                    old.setSuccess(false);
                    completed.add(old);
                    eventSink.publishTaskStageFailed(planId, taskId, stageName, stageRes.getMessage(), 0);
                    eventSink.publishTaskFailed(planId, taskId, stageRes.getMessage(), 0);
                    // RF-13: 使用 stateManager (如果有)，不再直接调用 setStatus
                    if (stateManager != null) {
                        stateManager.updateState(taskId, TaskStatus.FAILED);
                    }
                    stopHeartbeat();
                    metrics.incrementCounter("task_failed");
                    // SC-02: release on terminal
                    if (conflictManager != null) conflictManager.releaseTask(task.getTenantId());
                    return TaskExecutionResult.fail(planId, taskId, task.getStatus(), stageRes.getMessage(), Duration.between(start, LocalDateTime.now()), completed);
                }

                // 暂停检查：仅在 Stage 边界
                if (context.isPauseRequested()) {
                    // RF-13: 使用 stateManager
                    if (stateManager != null) {
                        stateManager.updateState(taskId, TaskStatus.PAUSED);
                    }
                    eventSink.publishTaskPaused(planId, taskId, 0);
                    stopHeartbeat();
                    metrics.incrementCounter("task_paused");
                    return TaskExecutionResult.ok(planId, taskId, task.getStatus(), Duration.between(start, LocalDateTime.now()), completed);
                }
                if (context.isCancelRequested()) {
                    // RF-13: 使用 stateManager
                    if (stateManager != null) {
                        stateManager.updateState(taskId, TaskStatus.CANCELLED);
                    }
                    eventSink.publishTaskCancelled(planId, taskId, 0);
                    stopHeartbeat();
                    metrics.incrementCounter("task_cancelled");
                    return TaskExecutionResult.ok(planId, taskId, task.getStatus(), Duration.between(start, LocalDateTime.now()), completed);
                }
            }

            // RF-13: 使用 stateManager
            if (stateManager != null) {
                stateManager.updateState(taskId, TaskStatus.COMPLETED);
            }
            Duration d = Duration.between(start, LocalDateTime.now());
            eventSink.publishTaskCompleted(planId, taskId, d, names(completed), 0);
            checkpointService.clearCheckpoint(task);
            stopHeartbeat();
            metrics.incrementCounter("task_completed");
            // SC-02: release on terminal
            if (conflictManager != null) conflictManager.releaseTask(task.getTenantId());
            return TaskExecutionResult.ok(planId, taskId, task.getStatus(), d, completed);
        } finally {
            context.clearMdc();
        }
    }

    private void stopHeartbeat() {
        if (heartbeatScheduler != null) heartbeatScheduler.stop();
    }

    public int getCompletedStageCount() { return completedCounter.get(); }

    /**
     * 回滚执行：逆序执行各 Stage 的 rollback，并发布事件。仅示例，不接线旧流。
     */
    public TaskExecutionResult rollback() {
        String taskId = task.getTaskId();
        eventSink.publishTaskRollingBack(planId, taskId, 0);
        // RF-13: 使用 stateManager
        if (stateManager != null) {
            stateManager.updateState(taskId, TaskStatus.ROLLING_BACK);
        }
        List<StageResult> rollbackStages = new ArrayList<>();
        List<TaskStage> copy = new ArrayList<>(stages);
        for (int i = copy.size() - 1; i >= 0; i--) {
            TaskStage s = copy.get(i);
            String name = s.getName();
            eventSink.publishTaskStageRollingBack(planId, taskId, name, 0);
            boolean success = true;
            try {
                boolean invoked = false;
                if (s instanceof CompositeServiceStage) {
                    RollbackStrategy rs = ((CompositeServiceStage) s).getRollbackStrategy();
                    if (rs != null) {
                        rs.rollback(task, context);
                        invoked = true;
                    }
                }
                if (!invoked) {
                    // 调用通用 Stage 回滚
                    s.rollback(context);
                }
            } catch (Exception ex) {
                success = false;
                eventSink.publishTaskStageRollbackFailed(planId, taskId, name, ex.getMessage(), 0);
            }
            StageResult sr = new StageResult();
            sr.setStageName(name);
            sr.setSuccess(success);
            rollbackStages.add(sr);
            if (success) {
                eventSink.publishTaskStageRolledBack(planId, taskId, name, 0);
            }
        }
        boolean anyFailed = rollbackStages.stream().anyMatch(r -> !r.isSuccess());
        // RF-13: 使用 stateManager
        if (stateManager != null) {
            stateManager.updateState(taskId, anyFailed ? TaskStatus.ROLLBACK_FAILED : TaskStatus.ROLLED_BACK);
        }
        if (anyFailed) {
            List<String> partial = new ArrayList<>();
            for (StageResult r : rollbackStages) if (r.isSuccess()) partial.add(r.getStageName());
            eventSink.publishTaskRollbackFailed(planId, taskId, partial, "one or more stages failed to rollback", 0);
        } else {
            eventSink.publishTaskRolledBack(planId, taskId, 0);
        }
        // SC-02: release on terminal
        if (conflictManager != null) conflictManager.releaseTask(task.getTenantId());
        metrics.incrementCounter("rollback_count");
        return TaskExecutionResult.ok(planId, taskId, task.getStatus(), Duration.ZERO, rollbackStages);
    }

    public TaskExecutionResult retry(boolean fromCheckpoint) {
        String taskId = task.getTaskId();
        eventSink.publishTaskRetryStarted(planId, taskId, fromCheckpoint);
        TaskExecutionResult result;
        if (fromCheckpoint) {
            result = execute();
        } else {
            // RF-13: 不再直接设置 currentStageIndex，由 retry() 业务方法处理
            // 注意：这里应该调用 task.retry(fromCheckpoint, globalMaxRetry) 来重置
            if (stateManager != null) {
                stateManager.updateState(taskId, TaskStatus.PENDING);
            }
            checkpointService.clearCheckpoint(task);
            // 重试前确保心跳可重新启动
            if (heartbeatScheduler != null) heartbeatScheduler.stop();
            result = execute();
        }
        eventSink.publishTaskRetryCompleted(planId, taskId, fromCheckpoint);
        return result;
    }

    public TaskExecutionResult invokeRollback() {
        return rollback();
    }

    private List<String> names(List<StageResult> results) {
        List<String> names = new ArrayList<>();
        for (StageResult r : results) {
            names.add(r.getStageName());
        }
        return names;
    }
}
