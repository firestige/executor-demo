package xyz.firestige.executor.state;

import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.state.event.*;
import xyz.firestige.executor.domain.state.TaskStateMachine;
import xyz.firestige.executor.domain.state.ctx.TaskTransitionContext;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.domain.task.TenantDeployConfigSnapshot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务状态管理器
 * 管理任务状态，并在状态转移时发布事件
 */
public class TaskStateManager {

    /**
     * 任务状态机映射
     * Key: taskId, Value: TaskStateMachine
     */
    private final Map<String, TaskStateMachine> stateMachines = new ConcurrentHashMap<>();

    /**
     * Spring 事件发布器
     */
    private ApplicationEventPublisher eventPublisher;

    private final Map<String, Long> sequences = new ConcurrentHashMap<>();
    // 新增：保存聚合与运行上下文及总阶段数
    private final Map<String, TaskAggregate> aggregates = new ConcurrentHashMap<>();
    private final Map<String, TaskRuntimeContext> runtimeContexts = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalStagesMap = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<String>> stageNamesMap = new ConcurrentHashMap<>();
    private xyz.firestige.executor.service.health.RollbackHealthVerifier rollbackHealthVerifier = new xyz.firestige.executor.service.health.AlwaysTrueRollbackHealthVerifier();

    public TaskStateManager() {
    }

    public TaskStateManager(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 初始化任务状态
     */
    public void initializeTask(String taskId, TaskStatus initialStatus) {
        stateMachines.put(taskId, new TaskStateMachine(initialStatus));
        sequences.put(taskId, 0L);
    }

    /**
     * 注册聚合并配置 Guards/Actions（在聚合可用后）
     */
    public void registerTaskAggregate(String taskId, TaskAggregate aggregate, TaskRuntimeContext runtimeCtx, int totalStages) {
        aggregates.put(taskId, aggregate);
        runtimeContexts.put(taskId, runtimeCtx);
        totalStagesMap.put(taskId, totalStages);
        TaskStateMachine sm = stateMachines.get(taskId);
        if (sm == null) return; // ensure initializeTask called first
        // Guards
        sm.registerGuard(TaskStatus.FAILED, TaskStatus.RUNNING, ctx -> ctx.getAggregate().getRetryCount() < (ctx.getAggregate().getMaxRetry() != null ? ctx.getAggregate().getMaxRetry() : 3));
        sm.registerGuard(TaskStatus.RUNNING, TaskStatus.PAUSED, ctx -> ctx.getContext() != null && ctx.getContext().isPauseRequested());
        sm.registerGuard(TaskStatus.PAUSED, TaskStatus.RUNNING, ctx -> ctx.getContext() != null && !ctx.getContext().isPauseRequested());
        sm.registerGuard(TaskStatus.RUNNING, TaskStatus.COMPLETED, ctx -> ctx.getAggregate().getCurrentStageIndex() >= ctx.getTotalStages());
        // Actions
        sm.registerAction(TaskStatus.PENDING, TaskStatus.RUNNING, ctx -> ctx.getAggregate().setStartedAt(java.time.LocalDateTime.now()));
        sm.registerAction(TaskStatus.RUNNING, TaskStatus.COMPLETED, ctx -> ctx.getAggregate().setEndedAt(java.time.LocalDateTime.now()));
        sm.registerAction(TaskStatus.RUNNING, TaskStatus.FAILED, ctx -> ctx.getAggregate().setEndedAt(java.time.LocalDateTime.now()));
    }

    /**
     * 更新状态
     */
    public void updateState(String taskId, TaskStatus newStatus) {
        updateState(taskId, newStatus, null, null);
    }

    /**
     * 更新状态（带失败信息）
     */
    public void updateState(String taskId, TaskStatus newStatus, FailureInfo failureInfo) {
        updateState(taskId, newStatus, failureInfo, null);
    }

    /**
     * 更新状态（完整版）
     */
    public void updateState(String taskId, TaskStatus newStatus, FailureInfo failureInfo, String message) {
        TaskStateMachine sm = stateMachines.get(taskId);
        if (sm == null) {
            // 如果状态机不存在，创建一个新的
            initializeTask(taskId, newStatus);
            return;
        }
        TaskAggregate agg = aggregates.get(taskId);
        TaskRuntimeContext rctx = runtimeContexts.get(taskId);
        Integer totalStages = totalStagesMap.get(taskId);
        if (agg == null || totalStages == null) {
            // 尚未注册聚合，跳过迁移以避免 NPE；后续注册后会再调用
            return;
        }
        TaskTransitionContext txCtx = new TaskTransitionContext(agg, rctx, totalStages);
        TaskStatus old = sm.getCurrent();
        TaskStatus after = sm.transitionTo(newStatus, txCtx);
        boolean transitioned = after != null && after != old && after == newStatus;
        if (transitioned) {
            applyActionsOnTransition(taskId, old, newStatus);
            if (eventPublisher != null) {
                // 根据新状态创建并发布对应的事件
                TaskStatusEvent event = createEventForStatus(taskId, newStatus, failureInfo, message);
                if (event != null) eventPublisher.publishEvent(event);
            }
        }
    }

    public void registerStageNames(String taskId, java.util.List<String> stageNames) {
        if (stageNames != null) stageNamesMap.put(taskId, new ArrayList<>(stageNames));
    }

    public void setRollbackHealthVerifier(xyz.firestige.executor.service.health.RollbackHealthVerifier v) {
        if (v != null) this.rollbackHealthVerifier = v;
    }

    private void applyActionsOnTransition(String taskId, TaskStatus from, TaskStatus to) {
        TaskAggregate agg = aggregates.get(taskId);
        if (agg == null) return;
        // keep aggregate status in sync with state machine
        agg.setStatus(to);
        if (from == TaskStatus.PENDING && to == TaskStatus.RUNNING) {
            if (agg.getStartedAt() == null) agg.setStartedAt(java.time.LocalDateTime.now());
        }
        if (from == TaskStatus.RUNNING && (to == TaskStatus.COMPLETED || to == TaskStatus.FAILED)) {
            agg.setEndedAt(java.time.LocalDateTime.now());
            if (agg.getStartedAt() != null && agg.getEndedAt() != null) {
                long ms = Duration.between(agg.getStartedAt(), agg.getEndedAt()).toMillis();
                agg.setDurationMillis(ms);
            }
        }
        if (from == TaskStatus.ROLLING_BACK && (to == TaskStatus.ROLLED_BACK || to == TaskStatus.ROLLBACK_FAILED)) {
            agg.setEndedAt(java.time.LocalDateTime.now());
            if (agg.getStartedAt() != null && agg.getEndedAt() != null) {
                long ms = Duration.between(agg.getStartedAt(), agg.getEndedAt()).toMillis();
                agg.setDurationMillis(ms);
            }
            if (to == TaskStatus.ROLLED_BACK) {
                // 恢复上一版可用配置
                TenantDeployConfigSnapshot snap = agg.getPrevConfigSnapshot();
                if (snap != null) {
                    agg.setDeployUnitId(snap.getDeployUnitId());
                    agg.setDeployUnitVersion(snap.getDeployUnitVersion());
                    agg.setDeployUnitName(snap.getDeployUnitName());
                    // RB-02：仅在健康确认成功时更新 lastKnownGoodVersion
                    TaskRuntimeContext ctx = runtimeContexts.get(taskId);
                    if (rollbackHealthVerifier == null || rollbackHealthVerifier.verify(agg, ctx)) {
                        agg.setLastKnownGoodVersion(snap.getDeployUnitVersion());
                    }
                }
            }
        }
    }

    /**
     * 获取任务当前状态
     */
    public TaskStatus getState(String taskId) {
        TaskStateMachine sm = stateMachines.get(taskId);
        return sm != null ? sm.getCurrent() : null;
    }

    /**
     * 获取状态机
     */
    public TaskStateMachine getStateMachine(String taskId) {
        return stateMachines.get(taskId);
    }

    /**
     * 移除任务状态
     */
    public void removeTask(String taskId) {
        stateMachines.remove(taskId);
        aggregates.remove(taskId);
        runtimeContexts.remove(taskId);
        totalStagesMap.remove(taskId);
    }

    /**
     * 发布任务创建事件
     */
    public void publishTaskCreatedEvent(String taskId, int configCount) {
        if (eventPublisher != null) {
            TaskCreatedEvent event = new TaskCreatedEvent(taskId, configCount, null);
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务校验失败事件
     */
    public void publishTaskValidationFailedEvent(String taskId, FailureInfo failureInfo, List<xyz.firestige.executor.validation.ValidationError> validationErrors) {
        if (eventPublisher != null) {
            TaskValidationFailedEvent event = new TaskValidationFailedEvent(taskId, failureInfo, validationErrors);
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务校验通过事件
     */
    public void publishTaskValidatedEvent(String taskId, int validatedCount) {
        if (eventPublisher != null) {
            TaskValidatedEvent event = new TaskValidatedEvent(taskId, validatedCount);
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务开始执行事件
     */
    public void publishTaskStartedEvent(String taskId, int totalStages) {
        if (eventPublisher != null) {
            TaskStartedEvent event = new TaskStartedEvent(taskId, totalStages);
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务进度事件
     */
    public void publishTaskProgressEvent(String taskId, String currentStage, int completedStages, int totalStages) {
        if (eventPublisher != null) {
            TaskProgressEvent event = new TaskProgressEvent(taskId, currentStage, completedStages, totalStages);
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布 Stage 完成事件
     */
    public void publishTaskStageCompletedEvent(String taskId, String stageName, xyz.firestige.executor.execution.StageResult stageResult) {
        if (eventPublisher != null) {
            TaskStageCompletedEvent event = new TaskStageCompletedEvent(taskId, stageName, stageResult);
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布 Stage 失败事件
     */
    public void publishTaskStageFailedEvent(String taskId, String stageName, FailureInfo failureInfo) {
        if (eventPublisher != null) {
            TaskStageFailedEvent event = new TaskStageFailedEvent(taskId, stageName, failureInfo);
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务失败事件
     */
    public void publishTaskFailedEvent(String taskId, FailureInfo failureInfo, List<String> completedStages, String failedStage) {
        if (eventPublisher != null) {
            TaskFailedEvent event = new TaskFailedEvent(taskId, failureInfo, completedStages, failedStage);
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务完成事件
     */
    public void publishTaskCompletedEvent(String taskId, Duration duration, List<String> completedStages) {
        if (eventPublisher != null) {
            TaskCompletedEvent event = new TaskCompletedEvent(taskId, duration, completedStages);
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务暂停事件
     */
    public void publishTaskPausedEvent(String taskId, String pausedBy, String currentStage) {
        if (eventPublisher != null) {
            TaskPausedEvent event = new TaskPausedEvent(taskId, pausedBy, currentStage);
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务恢复事件
     */
    public void publishTaskResumedEvent(String taskId, String resumedBy, String resumeFromStage) {
        if (eventPublisher != null) {
            TaskResumedEvent event = new TaskResumedEvent(taskId, resumedBy, resumeFromStage);
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务回滚中事件
     */
    public void publishTaskRollingBackEvent(String taskId, String reason, List<String> stagesToRollback) {
        if (eventPublisher != null) {
            TaskRollingBackEvent event = new TaskRollingBackEvent(taskId, reason, stagesToRollback);
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务回滚失败事件
     */
    public void publishTaskRollbackFailedEvent(String taskId, FailureInfo failureInfo, List<String> partiallyRolledBackStages) {
        if (eventPublisher != null) {
            TaskRollbackFailedEvent event = new TaskRollbackFailedEvent(taskId, failureInfo, partiallyRolledBackStages);
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务回滚完成事件
     */
    public void publishTaskRolledBackEvent(String taskId, List<String> rolledBackStages) {
        if (eventPublisher != null) {
            TaskRolledBackEvent event = new TaskRolledBackEvent(taskId, rolledBackStages);
            TaskAggregate agg = aggregates.get(taskId);
            if (agg != null && agg.getPrevConfigSnapshot() != null) {
                TenantDeployConfigSnapshot snap = agg.getPrevConfigSnapshot();
                event.setPrevDeployUnitId(snap.getDeployUnitId());
                event.setPrevDeployUnitVersion(snap.getDeployUnitVersion());
                event.setPrevDeployUnitName(snap.getDeployUnitName());
            }
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务取消事件
     */
    public void publishTaskCancelledEvent(String taskId) {
        if (eventPublisher != null) {
            TaskCancelledEvent event = new TaskCancelledEvent(taskId);
            TaskAggregate agg = aggregates.get(taskId);
            if (agg != null) {
                event.setCancelledBy("system");
                int idx = agg.getCurrentStageIndex() - 1;
                String last = null;
                java.util.List<String> names = stageNamesMap.get(taskId);
                if (names != null && idx >= 0 && idx < names.size()) {
                    last = names.get(idx);
                }
                event.setLastStage(last);
            }
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务取消事件
     */
    public void publishTaskCancelledEvent(String taskId, String cancelledBy) {
        if (eventPublisher != null) {
            TaskCancelledEvent event = new TaskCancelledEvent(taskId);
            TaskAggregate agg = aggregates.get(taskId);
            event.setCancelledBy(cancelledBy);
            if (agg != null) {
                int idx = agg.getCurrentStageIndex() - 1;
                String last = null;
                java.util.List<String> names = stageNamesMap.get(taskId);
                if (names != null && idx >= 0 && idx < names.size()) {
                    last = names.get(idx);
                }
                event.setLastStage(last);
            }
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务重试开始事件
     */
    public void publishTaskRetryStartedEvent(String taskId, boolean fromCheckpoint) {
        if (eventPublisher != null) {
            TaskRetryStartedEvent ev = new TaskRetryStartedEvent(taskId, fromCheckpoint);
            ev.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(ev);
        }
    }

    /**
     * 发布任务重试完成事件
     */
    public void publishTaskRetryCompletedEvent(String taskId, boolean fromCheckpoint) {
        if (eventPublisher != null) {
            TaskRetryCompletedEvent ev = new TaskRetryCompletedEvent(taskId, fromCheckpoint);
            ev.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(ev);
        }
    }

    /**
     * 根据状态创建对应的事件（简化版）
     */
    private TaskStatusEvent createEventForStatus(String taskId, TaskStatus status, FailureInfo failureInfo, String message) {
        TaskStatusEvent event = null;

        switch (status) {
            case CREATED:
                event = new TaskCreatedEvent(taskId, 0, null);
                break;
            case VALIDATION_FAILED:
                event = new TaskValidationFailedEvent(taskId, failureInfo, null);
                break;
            case PENDING:
                event = new TaskValidatedEvent(taskId, 0);
                break;
            case RUNNING:
                event = new TaskStartedEvent(taskId, 0);
                break;
            case PAUSED:
                event = new TaskPausedEvent(taskId, null, null);
                break;
            case RESUMING:
                event = new TaskResumedEvent(taskId, null, null);
                break;
            case COMPLETED:
                event = new TaskCompletedEvent(taskId, null, null);
                break;
            case FAILED:
                event = new TaskFailedEvent(taskId, failureInfo, null, null);
                break;
            case ROLLING_BACK:
                event = new TaskRollingBackEvent(taskId, message, null);
                break;
            case ROLLBACK_FAILED:
                event = new TaskRollbackFailedEvent(taskId, failureInfo, null);
                break;
            case ROLLED_BACK:
                event = new TaskRolledBackEvent(taskId, null);
                break;
            case CANCELLED:
                event = new TaskCancelledEvent(taskId);
                break;
            default:
                break;
        }

        if (event != null) {
            event.setSequenceId(nextSeq(taskId));
            if (message != null) {
                event.setMessage(message);
            }
        }
        return event;
    }

    // Getters and Setters

    public ApplicationEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    // helper
    private long nextSeq(String taskId) {
        return sequences.compute(taskId, (k,v) -> v == null ? 1L : v + 1L);
    }

    public String getTenantId(String taskId) {
        TaskAggregate agg = aggregates.get(taskId);
        return agg != null ? agg.getTenantId() : null;
    }
}
