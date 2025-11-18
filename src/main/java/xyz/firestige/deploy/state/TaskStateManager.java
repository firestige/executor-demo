package xyz.firestige.deploy.state;

import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.deploy.exception.FailureInfo;
import xyz.firestige.deploy.execution.StageResult;
import xyz.firestige.deploy.state.event.TaskCancelledEvent;
import xyz.firestige.deploy.domain.state.TaskStateMachine;
import xyz.firestige.deploy.domain.state.ctx.TaskTransitionContext;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TenantDeployConfigSnapshot;
import xyz.firestige.deploy.state.event.TaskCompletedEvent;
import xyz.firestige.deploy.state.event.TaskCreatedEvent;
import xyz.firestige.deploy.state.event.TaskFailedEvent;
import xyz.firestige.deploy.state.event.TaskPausedEvent;
import xyz.firestige.deploy.state.event.TaskProgressEvent;
import xyz.firestige.deploy.state.event.TaskResumedEvent;
import xyz.firestige.deploy.state.event.TaskRetryCompletedEvent;
import xyz.firestige.deploy.state.event.TaskRetryStartedEvent;
import xyz.firestige.deploy.state.event.TaskRollbackFailedEvent;
import xyz.firestige.deploy.state.event.TaskRolledBackEvent;
import xyz.firestige.deploy.state.event.TaskRollingBackEvent;
import xyz.firestige.deploy.state.event.TaskStageCompletedEvent;
import xyz.firestige.deploy.state.event.TaskStageFailedEvent;
import xyz.firestige.deploy.state.event.TaskStartedEvent;
import xyz.firestige.deploy.state.event.TaskStatusEvent;
import xyz.firestige.deploy.state.event.TaskValidatedEvent;
import xyz.firestige.deploy.state.event.TaskValidationFailedEvent;
import xyz.firestige.deploy.state.strategy.*;
import xyz.firestige.deploy.validation.ValidationError;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务状态管理器（RF-13 重构版）
 * 使用策略模式管理状态转换，委托给聚合的业务方法
 */
public class TaskStateManager {

    /**
     * 任务状态机映射（保留兼容性）
     * Key: taskId, Value: TaskStateMachine
     */
    private final Map<String, TaskStateMachine> stateMachines = new ConcurrentHashMap<>();

    /**
     * Spring 事件发布器
     */
    private ApplicationEventPublisher eventPublisher;

    private final Map<String, Long> sequences = new ConcurrentHashMap<>();
    // RF-13: 保存聚合与运行上下文
    private final Map<String, TaskAggregate> aggregates = new ConcurrentHashMap<>();
    private final Map<String, TaskRuntimeContext> runtimeContexts = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalStagesMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> stageNamesMap = new ConcurrentHashMap<>();

    /**
     * RF-13: 状态转换策略注册表
     */
    private final Map<StateTransitionKey, StateTransitionStrategy> strategies = new HashMap<>();
    private Integer globalMaxRetry;

    public TaskStateManager() {
        initializeStrategies();
    }

    public TaskStateManager(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        initializeStrategies();
    }

    public TaskStateManager(ApplicationEventPublisher eventPublisher, Integer globalMaxRetry) {
        this.eventPublisher = eventPublisher;
        this.globalMaxRetry = globalMaxRetry;
        initializeStrategies();
    }

    /**
     * RF-13: 初始化所有状态转换策略
     */
    private void initializeStrategies() {
        // 1. CREATED -> PENDING
        registerStrategy(new MarkAsPendingTransitionStrategy());
        
        // 2. PENDING -> RUNNING (启动)
        registerStrategy(new StartTransitionStrategy());
        
        // 3. RUNNING -> PAUSED (暂停)
        registerStrategy(new PauseTransitionStrategy());
        
        // 4. PAUSED -> RUNNING (恢复)
        registerStrategy(new ResumeTransitionStrategy());
        
        // 5. RUNNING -> COMPLETED (完成)
        registerStrategy(new CompleteTransitionStrategy(null)); // totalStages 动态获取
        
        // 6. RUNNING -> FAILED (失败)
        registerStrategy(new FailTransitionStrategy());
        
        // 7. FAILED/ROLLED_BACK -> RUNNING (重试)
        registerStrategy(new RetryTransitionStrategy(globalMaxRetry));
        
        // 8. * -> ROLLING_BACK (开始回滚)
        registerStrategy(new RollbackTransitionStrategy());
        
        // 9. ROLLING_BACK -> ROLLED_BACK (回滚完成)
        registerStrategy(new RollbackCompleteTransitionStrategy());
        
        // 10. ROLLING_BACK -> ROLLBACK_FAILED (回滚失败)
        registerStrategy(new RollbackFailTransitionStrategy());
        
        // 11. * -> CANCELLED (取消)
        registerStrategy(new CancelTransitionStrategy());
    }

    /**
     * RF-13: 注册策略
     */
    private void registerStrategy(StateTransitionStrategy strategy) {
        StateTransitionKey key = new StateTransitionKey(strategy.getFromStatus(), strategy.getToStatus());
        strategies.put(key, strategy);
    }

    /**
     * 初始化任务状态
     */
    public void initializeTask(String taskId, TaskStatus initialStatus) {
        stateMachines.put(taskId, new TaskStateMachine(initialStatus));
        sequences.put(taskId, 0L);
    }

    /**
     * RF-13: 注册聚合（简化版，不再需要配置 Guards/Actions）
     * 状态转换逻辑由策略模式接管
     */
    public void registerTaskAggregate(String taskId, TaskAggregate aggregate, TaskRuntimeContext runtimeCtx, int totalStages) {
        aggregates.put(taskId, aggregate);
        runtimeContexts.put(taskId, runtimeCtx);
        totalStagesMap.put(taskId, totalStages);
        // RF-13: 不再需要配置 Guards/Actions，由策略模式接管
    }

    /**
     * RF-13: 更新状态（使用策略模式）
     */
    public void updateState(String taskId, TaskStatus newStatus) {
        updateState(taskId, newStatus, null, null, null);
    }

    /**
     * RF-13: 更新状态（带失败信息）
     */
    public void updateState(String taskId, TaskStatus newStatus, FailureInfo failureInfo) {
        updateState(taskId, newStatus, failureInfo, null, null);
    }

    /**
     * RF-13: 更新状态（完整版）
     */
    public void updateState(String taskId, TaskStatus newStatus, FailureInfo failureInfo, String message) {
        updateState(taskId, newStatus, failureInfo, message, null);
    }

    /**
     * RF-13: 更新状态（策略模式实现）
     * 
     * @param taskId Task ID
     * @param newStatus 目标状态
     * @param failureInfo 失败信息（可选）
     * @param message 消息（可选）
     * @param additionalData 策略需要的额外数据（可选）
     */
    public void updateState(String taskId, TaskStatus newStatus, FailureInfo failureInfo, String message, Object additionalData) {
        TaskAggregate agg = aggregates.get(taskId);
        if (agg == null) {
            // 尚未注册聚合，跳过
            return;
        }
        
        TaskStatus oldStatus = agg.getStatus();
        TaskRuntimeContext rctx = runtimeContexts.get(taskId);
        
        // 1. 查找策略
        StateTransitionKey key = new StateTransitionKey(oldStatus, newStatus);
        StateTransitionStrategy strategy = strategies.get(key);
        
        // 2. 特殊处理：重试策略支持两种源状态
        if (strategy == null && newStatus == TaskStatus.RUNNING) {
            if (oldStatus == TaskStatus.FAILED || oldStatus == TaskStatus.ROLLED_BACK) {
                strategy = strategies.get(new StateTransitionKey(TaskStatus.FAILED, TaskStatus.RUNNING));
            }
        }
        
        // 3. 特殊处理：任意状态转换（取消和回滚）
        if (strategy == null) {
            if (newStatus == TaskStatus.CANCELLED) {
                strategy = strategies.get(new StateTransitionKey(null, TaskStatus.CANCELLED));
            } else if (newStatus == TaskStatus.ROLLING_BACK) {
                strategy = strategies.get(new StateTransitionKey(null, TaskStatus.ROLLING_BACK));
            }
        }
        
        if (strategy == null) {
            // 没有找到策略，跳过或抛异常
            return;
        }
        
        // 4. 检查前置条件
        if (!strategy.canTransition(agg, rctx, newStatus)) {
            // 前置条件不满足，跳过
            return;
        }
        
        // 5. 执行策略（委托给聚合）
        try {
            strategy.execute(agg, rctx, additionalData);
        } catch (Exception e) {
            // 策略执行失败，记录日志并跳过
            return;
        }
        
        // 6. 同步状态机（保持兼容性）
        TaskStateMachine sm = stateMachines.get(taskId);
        if (sm != null) {
            Integer totalStages = totalStagesMap.get(taskId);
            TaskTransitionContext txCtx = new TaskTransitionContext(agg, rctx, totalStages != null ? totalStages : 0);
            sm.transitionTo(newStatus, txCtx);
        }
        
        // 7. 发布事件
        if (eventPublisher != null) {
            TaskStatusEvent event = createEventForStatus(taskId, newStatus, failureInfo, message);
            if (event != null) {
                eventPublisher.publishEvent(event);
            }
        }
    }

    public void registerStageNames(String taskId, List<String> stageNames) {
        if (stageNames != null) stageNamesMap.put(taskId, new ArrayList<>(stageNames));
    }

    /**
     * 获取下一个序列号（RF-11）
     * 用于事件的 sequenceId，保证单调递增
     *
     * @param taskId Task ID
     * @return 下一个序列号
     */
    public long nextSequenceId(String taskId) {
        return sequences.compute(taskId, (k, v) -> (v == null ? 0L : v) + 1);
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
    public void publishTaskValidationFailedEvent(String taskId, FailureInfo failureInfo, List<ValidationError> validationErrors) {
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
    public void publishTaskStageCompletedEvent(String taskId, String stageName, StageResult stageResult) {
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
                List<String> names = stageNamesMap.get(taskId);
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
                List<String> names = stageNamesMap.get(taskId);
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
