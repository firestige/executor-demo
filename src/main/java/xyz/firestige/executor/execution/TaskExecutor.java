package xyz.firestige.executor.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskContext;
import xyz.firestige.executor.event.TaskEventSink;
import xyz.firestige.executor.state.TaskStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 新 TaskExecutor（不接线旧流程）：
 * - 注入 MDC（通过 TaskContext 提供）
 * - 每 10 秒发布 TaskProgressEvent（由上层注入的事件下沉处理）
 * - 在 Stage 边界使用 CheckpointService 保存/恢复检查点
 */
public class TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    private final String planId;
    private final TaskAggregate task;
    private final List<TaskStage> stages;
    private final TaskContext context;
    private final CheckpointService checkpointService;
    private final TaskEventSink eventSink;
    private final int progressIntervalSeconds;
    private volatile HeartbeatScheduler heartbeatScheduler; // 允许后置注入
    private final java.util.concurrent.atomic.AtomicInteger completedCounter = new java.util.concurrent.atomic.AtomicInteger();

    public TaskExecutor(String planId,
                        TaskAggregate task,
                        List<TaskStage> stages,
                        TaskContext context,
                        CheckpointService checkpointService,
                        TaskEventSink eventSink,
                        int progressIntervalSeconds) {
        this.planId = planId;
        this.task = task;
        this.stages = stages != null ? stages : new ArrayList<>();
        this.context = context;
        this.checkpointService = checkpointService;
        this.eventSink = eventSink;
        this.progressIntervalSeconds = progressIntervalSeconds <= 0 ? 10 : progressIntervalSeconds;
    }

    public TaskExecutor(String planId,
                        TaskAggregate task,
                        List<TaskStage> stages,
                        TaskContext context,
                        CheckpointService checkpointService,
                        TaskEventSink eventSink,
                        int progressIntervalSeconds,
                        HeartbeatScheduler heartbeatScheduler) {
        this(planId, task, stages, context, checkpointService, eventSink, progressIntervalSeconds);
        this.heartbeatScheduler = heartbeatScheduler;
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
        LocalDateTime start = LocalDateTime.now();
        eventSink.publishTaskStarted(planId, taskId, stages.size(), 0);
        task.setStatus(TaskStatus.RUNNING);

        List<StageResult> completed = new ArrayList<>();

        // 从检查点恢复
        var cp = checkpointService.loadCheckpoint(task);
        int startIndex = (cp != null) ? cp.getLastCompletedStageIndex() + 1 : 0;
        completedCounter.set(startIndex); // 已完成数量初始化

        if (heartbeatScheduler != null) {
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
                    task.setCurrentStageIndex(i + 1); // 更新完成 stage 索引
                } else {
                    StageResult old = new StageResult();
                    old.setStageName(stageName);
                    old.setSuccess(false);
                    completed.add(old);
                    eventSink.publishTaskStageFailed(planId, taskId, stageName, stageRes.getMessage(), 0);
                    eventSink.publishTaskFailed(planId, taskId, stageRes.getMessage(), 0);
                    stopHeartbeat();
                    return TaskExecutionResult.fail(planId, taskId, task.getStatus(), stageRes.getMessage(), Duration.between(start, LocalDateTime.now()), completed);
                }

                // 暂停检查：仅在 Stage 边界
                if (context.isPauseRequested()) {
                    task.setStatus(TaskStatus.PAUSED);
                    eventSink.publishTaskPaused(planId, taskId, 0);
                    stopHeartbeat();
                    return TaskExecutionResult.ok(planId, taskId, task.getStatus(), Duration.between(start, LocalDateTime.now()), completed);
                }
                if (context.isCancelRequested()) {
                    task.setStatus(TaskStatus.CANCELLED);
                    eventSink.publishTaskCancelled(planId, taskId, 0);
                    stopHeartbeat();
                    return TaskExecutionResult.ok(planId, taskId, task.getStatus(), Duration.between(start, LocalDateTime.now()), completed);
                }
            }

            task.setStatus(TaskStatus.COMPLETED);
            Duration d = Duration.between(start, LocalDateTime.now());
            eventSink.publishTaskCompleted(planId, taskId, d, names(completed), 0);
            checkpointService.clearCheckpoint(task);
            stopHeartbeat();
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
        task.setStatus(TaskStatus.ROLLING_BACK);
        List<StageResult> rollbackStages = new ArrayList<>();
        List<TaskStage> copy = new ArrayList<>(stages);
        Duration start = Duration.ofMillis(System.currentTimeMillis());
        for (int i = copy.size() - 1; i >= 0; i--) {
            TaskStage s = copy.get(i);
            String name = s.getName();
            eventSink.publishTaskStageRollingBack(planId, taskId, name, 0);
            try {
                s.rollback(context);
                StageResult sr = new StageResult();
                sr.setStageName(name);
                sr.setSuccess(true);
                rollbackStages.add(sr);
            } catch (Exception ex) {
                StageResult sr = new StageResult();
                sr.setStageName(name);
                sr.setSuccess(false);
                rollbackStages.add(sr);
                eventSink.publishTaskStageRollbackFailed(planId, taskId, name, ex.getMessage(), 0);
            }
        }
        // 统计失败
        boolean anyFailed = rollbackStages.stream().anyMatch(r -> !r.isSuccess());
        task.setStatus(anyFailed ? TaskStatus.ROLLBACK_FAILED : TaskStatus.ROLLED_BACK);
        eventSink.publishTaskRolledBack(planId, taskId, 0);
        return TaskExecutionResult.ok(planId, taskId, task.getStatus(), Duration.ZERO, rollbackStages);
    }

    public TaskExecutionResult retry(boolean fromCheckpoint) {
        if (fromCheckpoint) {
            // keep checkpoint; currentStageIndex already set by previous run
            return execute();
        } else {
            // reset state fully
            task.setCurrentStageIndex(0);
            task.setStatus(TaskStatus.PENDING);
            checkpointService.clearCheckpoint(task);
            return execute();
        }
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
