package xyz.firestige.executor.state;

import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.state.event.*;
import xyz.firestige.executor.domain.state.TaskStateMachine;
import xyz.firestige.executor.domain.state.ctx.TaskTransitionContext;

import java.time.Duration;
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
    private final Map<String, xyz.firestige.executor.domain.state.TaskStateMachine> stateMachines = new ConcurrentHashMap<>();

    /**
     * Spring 事件发布器
     */
    private ApplicationEventPublisher eventPublisher;

    private final Map<String, Long> sequences = new ConcurrentHashMap<>();

    public TaskStateManager() {
    }

    public TaskStateManager(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 初始化任务状态
     */
    public void initializeTask(String taskId, TaskStatus initialStatus) {
        TaskStateMachine sm = new TaskStateMachine(initialStatus);
        // 注册 Guards 与 Actions（简化版本）
        sm.registerGuard(TaskStatus.FAILED, TaskStatus.RUNNING, ctx -> ctx.getAggregate().getRetryCount() < (ctx.getAggregate().getMaxRetry() != null ? ctx.getAggregate().getMaxRetry() : 3));
        sm.registerGuard(TaskStatus.RUNNING, TaskStatus.PAUSED, ctx -> ctx.getContext() != null && ctx.getContext().isPauseRequested());
        sm.registerGuard(TaskStatus.RUNNING, TaskStatus.COMPLETED, ctx -> ctx.getAggregate().getCurrentStageIndex() >= ctx.getTotalStages());
        sm.registerAction(TaskStatus.PENDING, TaskStatus.RUNNING, ctx -> ctx.getAggregate().setStartedAt(java.time.LocalDateTime.now()));
        sm.registerAction(TaskStatus.RUNNING, TaskStatus.COMPLETED, ctx -> ctx.getAggregate().setEndedAt(java.time.LocalDateTime.now()));
        sm.registerAction(TaskStatus.RUNNING, TaskStatus.FAILED, ctx -> ctx.getAggregate().setEndedAt(java.time.LocalDateTime.now()));
        stateMachines.put(taskId, sm);
        sequences.put(taskId, 0L);
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
        TaskStatus old = sm.getCurrent();
        // 构造迁移上下文（task 聚合后续由调用方传入，这里暂仅 totalStages=0）
        TaskTransitionContext txCtx = new TaskTransitionContext(null, null, 0);
        TaskStatus after = sm.transitionTo(newStatus, txCtx);
        boolean transitioned = after != null && after != old && after == newStatus;
        if (transitioned && eventPublisher != null) {
            // 根据新状态创建并发布对应的事件
            TaskStatusEvent event = createEventForStatus(taskId, newStatus, failureInfo, message);
            if (event != null) eventPublisher.publishEvent(event);
        }
    }

    /**
     * 获取任务当前状态
     */
    public TaskStatus getState(String taskId) {
        xyz.firestige.executor.domain.state.TaskStateMachine sm = stateMachines.get(taskId);
        return sm != null ? sm.getCurrent() : null;
    }

    /**
     * 获取状态机
     */
    public xyz.firestige.executor.domain.state.TaskStateMachine getStateMachine(String taskId) {
        return stateMachines.get(taskId);
    }

    /**
     * 移除任务状态
     */
    public void removeTask(String taskId) {
        stateMachines.remove(taskId);
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
            event.setSequenceId(nextSeq(taskId));
            eventPublisher.publishEvent(event);
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
}
