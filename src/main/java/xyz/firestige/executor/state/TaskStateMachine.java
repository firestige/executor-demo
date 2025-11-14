package xyz.firestige.executor.state;

import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.exception.StateTransitionException;

import java.util.*;

/**
 * 任务状态机
 * 管理任务状态转移，确保状态转移的合法性
 */
public class TaskStateMachine {

    /**
     * 当前状态
     */
    private TaskStatus currentStatus;

    /**
     * 状态转移历史
     */
    private List<StateTransition> transitionHistory;

    /**
     * 状态转移规则映射
     * Key: 当前状态, Value: 允许转移到的状态列表
     */
    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITION_RULES = new HashMap<>();

    static {
        // CREATED 可以转到 VALIDATING
        TRANSITION_RULES.put(TaskStatus.CREATED, EnumSet.of(TaskStatus.VALIDATING));

        // VALIDATING 可以转到 VALIDATION_FAILED 或 PENDING
        TRANSITION_RULES.put(TaskStatus.VALIDATING, EnumSet.of(
                TaskStatus.VALIDATION_FAILED,
                TaskStatus.PENDING
        ));

        // VALIDATION_FAILED 是终态，不允许转移
        TRANSITION_RULES.put(TaskStatus.VALIDATION_FAILED, EnumSet.noneOf(TaskStatus.class));

        // PENDING 可以转到 RUNNING 或 CANCELLED
        TRANSITION_RULES.put(TaskStatus.PENDING, EnumSet.of(
                TaskStatus.RUNNING,
                TaskStatus.CANCELLED
        ));

        // RUNNING 可以转到 PAUSED, COMPLETED, FAILED, ROLLING_BACK
        TRANSITION_RULES.put(TaskStatus.RUNNING, EnumSet.of(
                TaskStatus.PAUSED,
                TaskStatus.COMPLETED,
                TaskStatus.FAILED,
                TaskStatus.ROLLING_BACK
        ));

        // PAUSED 可以转到 RESUMING, ROLLING_BACK, CANCELLED
        TRANSITION_RULES.put(TaskStatus.PAUSED, EnumSet.of(
                TaskStatus.RESUMING,
                TaskStatus.ROLLING_BACK,
                TaskStatus.CANCELLED
        ));

        // RESUMING 可以转到 RUNNING
        TRANSITION_RULES.put(TaskStatus.RESUMING, EnumSet.of(TaskStatus.RUNNING));

        // COMPLETED 是终态，但可以回滚
        TRANSITION_RULES.put(TaskStatus.COMPLETED, EnumSet.of(TaskStatus.ROLLING_BACK));

        // FAILED 可以转到 ROLLING_BACK 或 RUNNING（重试）
        TRANSITION_RULES.put(TaskStatus.FAILED, EnumSet.of(
                TaskStatus.ROLLING_BACK,
                TaskStatus.RUNNING
        ));

        // ROLLING_BACK 可以转到 ROLLED_BACK 或 ROLLBACK_FAILED
        TRANSITION_RULES.put(TaskStatus.ROLLING_BACK, EnumSet.of(
                TaskStatus.ROLLED_BACK,
                TaskStatus.ROLLBACK_FAILED
        ));

        // ROLLBACK_FAILED 可以重试回滚
        TRANSITION_RULES.put(TaskStatus.ROLLBACK_FAILED, EnumSet.of(TaskStatus.ROLLING_BACK));

        // ROLLED_BACK 是终态
        TRANSITION_RULES.put(TaskStatus.ROLLED_BACK, EnumSet.noneOf(TaskStatus.class));

        // CANCELLED 是终态
        TRANSITION_RULES.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));
    }

    public TaskStateMachine(TaskStatus initialStatus) {
        this.currentStatus = initialStatus;
        this.transitionHistory = new ArrayList<>();
    }

    /**
     * 转移到新状态
     */
    public synchronized StateTransitionResult transitionTo(TaskStatus newStatus, FailureInfo failureInfo) {
        // 检查是否可以转移
        if (!canTransition(currentStatus, newStatus)) {
            String errorMessage = String.format(
                    "不允许从状态 %s 转移到 %s",
                    currentStatus.getDescription(),
                    newStatus.getDescription()
            );
            return StateTransitionResult.failure(currentStatus, newStatus, errorMessage);
        }

        // 记录转移
        StateTransition transition = new StateTransition(currentStatus, newStatus);
        if (failureInfo != null) {
            transition.setFailureInfo(failureInfo);
            transition.setReason(failureInfo.getErrorMessage());
        }

        TaskStatus oldStatus = currentStatus;
        currentStatus = newStatus;
        transitionHistory.add(transition);

        // 创建成功结果（事件将在 TaskStateManager 中创建）
        return StateTransitionResult.success(oldStatus, newStatus, null);
    }

    /**
     * 转移到新状态（无失败信息）
     */
    public StateTransitionResult transitionTo(TaskStatus newStatus) {
        return transitionTo(newStatus, null);
    }

    /**
     * 检查是否可以转移
     */
    public boolean canTransition(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            return false;
        }

        Set<TaskStatus> allowedTransitions = TRANSITION_RULES.get(from);
        if (allowedTransitions == null) {
            return false;
        }

        return allowedTransitions.contains(to);
    }

    /**
     * 获取当前状态
     */
    public TaskStatus getCurrentStatus() {
        return currentStatus;
    }

    /**
     * 获取转移历史
     */
    public List<StateTransition> getTransitionHistory() {
        return new ArrayList<>(transitionHistory);
    }

    /**
     * 强制设置状态（仅用于特殊场景，慎用）
     */
    protected void forceSetStatus(TaskStatus status) {
        this.currentStatus = status;
    }

    /**
     * 验证状态转移并抛出异常
     */
    public void validateTransition(TaskStatus newStatus) {
        if (!canTransition(currentStatus, newStatus)) {
            throw new StateTransitionException(
                    String.format("不允许从状态 %s 转移到 %s",
                            currentStatus.getDescription(),
                            newStatus.getDescription()),
                    currentStatus.name(),
                    newStatus.name()
            );
        }
    }
}

