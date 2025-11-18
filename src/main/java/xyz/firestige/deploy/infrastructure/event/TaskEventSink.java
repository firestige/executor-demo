package xyz.firestige.deploy.infrastructure.event;

import java.time.Duration;
import java.util.List;

/**
 * 任务事件下沉接口（用于解耦 TaskExecutor 与具体事件发布实现）。
 * 所有方法均为可选实现；未接线时可使用 Noop 实现。
 */
public interface TaskEventSink {
    default void publishTaskStarted(String planId, String taskId, int totalStages, long sequenceId) {}
    default void publishTaskProgress(String planId, String taskId, double progress, long sequenceId) {}
    default void publishTaskProgressDetail(String planId, String taskId, int completedStages, int totalStages, long sequenceId) {}
    default void publishTaskStageStarted(String planId, String taskId, String stageName, long sequenceId) {}
    default void publishTaskStageSucceeded(String planId, String taskId, String stageName, Duration duration, long sequenceId) {}
    default void publishTaskStageFailed(String planId, String taskId, String stageName, String reason, long sequenceId) {}
    default void publishTaskPaused(String planId, String taskId, long sequenceId) {}
    default void publishTaskResumed(String planId, String taskId, long sequenceId) {}
    default void publishTaskCompleted(String planId, String taskId, Duration duration, List<String> completedStages, long sequenceId) {}
    default void publishTaskFailed(String planId, String taskId, String reason, long sequenceId) {}
    default void publishTaskCancelled(String planId, String taskId, long sequenceId) {}
    default void publishTaskRollingBack(String planId, String taskId, long sequenceId) {}
    default void publishTaskRollingBack(String planId, String taskId, List<String> stagesToRollback, long sequenceId) { publishTaskRollingBack(planId, taskId, sequenceId); }
    default void publishTaskStageRollingBack(String planId, String taskId, String stageName, long sequenceId) {}
    default void publishTaskStageRollbackFailed(String planId, String taskId, String stageName, String reason, long sequenceId) {}
    default void publishTaskStageRolledBack(String planId, String taskId, String stageName, long sequenceId) {}
    default void publishTaskRolledBack(String planId, String taskId, long sequenceId) {}
    default void publishTaskRolledBack(String planId, String taskId, List<String> rolledBackStages, long sequenceId) { publishTaskRolledBack(planId, taskId, sequenceId); }
    default void publishTaskRetryStarted(String planId, String taskId, boolean fromCheckpoint) {}
    default void publishTaskRetryCompleted(String planId, String taskId, boolean fromCheckpoint) {}
    default void publishTaskRollbackFailed(String planId, String taskId, List<String> partiallyRolledBackStages, String reason, long sequenceId) {}
}
