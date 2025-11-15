package xyz.firestige.executor.event;

import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.StageResult;

import java.time.Duration;
import java.util.List;

/**
 * 基于 TaskStateManager 的事件下沉实现，统一使用状态管理器生成的 sequenceId。
 */
public class SpringTaskEventSink implements TaskEventSink {

    private final TaskStateManager stateManager;

    public SpringTaskEventSink(TaskStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public void publishTaskStarted(String planId, String taskId, int totalStages, long sequenceIdIgnored) {
        stateManager.publishTaskStartedEvent(taskId, totalStages);
    }

    @Override
    public void publishTaskProgress(String planId, String taskId, double progress, long sequenceIdIgnored) {
        // progress 需要当前完成 Stage 数与 total；此处简单传递已完成数= (int)progress，实际接线时应使用精确值
        stateManager.publishTaskProgressEvent(taskId, null, (int) progress, 100); // 后续改为真实 totalStages
    }

    @Override
    public void publishTaskProgressDetail(String planId, String taskId, int completedStages, int totalStages, long sequenceIdIgnored) {
        stateManager.publishTaskProgressEvent(taskId, null, completedStages, totalStages);
    }

    @Override
    public void publishTaskStageStarted(String planId, String taskId, String stageName, long sequenceIdIgnored) {
        // 暂无单独事件，使用进度或状态迁移
    }

    @Override
    public void publishTaskStageSucceeded(String planId, String taskId, String stageName, Duration duration, long sequenceIdIgnored) {
        StageResult sr = new StageResult();
        sr.setStageName(stageName);
        sr.setSuccess(true);
        stateManager.publishTaskStageCompletedEvent(taskId, stageName, sr);
    }

    @Override
    public void publishTaskStageFailed(String planId, String taskId, String stageName, String reason, long sequenceIdIgnored) {
        FailureInfo fi = new FailureInfo();
        fi.setErrorMessage(reason);
        stateManager.publishTaskStageFailedEvent(taskId, stageName, fi);
    }

    @Override
    public void publishTaskPaused(String planId, String taskId, long sequenceIdIgnored) {
        stateManager.publishTaskPausedEvent(taskId, "system", null);
    }

    @Override
    public void publishTaskResumed(String planId, String taskId, long sequenceIdIgnored) {
        stateManager.publishTaskResumedEvent(taskId, "system", null);
    }

    @Override
    public void publishTaskCompleted(String planId, String taskId, Duration duration, List<String> completedStages, long sequenceIdIgnored) {
        stateManager.publishTaskCompletedEvent(taskId, duration, completedStages);
    }

    @Override
    public void publishTaskFailed(String planId, String taskId, String reason, long sequenceIdIgnored) {
        FailureInfo fi = new FailureInfo();
        fi.setErrorMessage(reason);
        stateManager.publishTaskFailedEvent(taskId, fi, null, null);
    }

    @Override
    public void publishTaskRollingBack(String planId, String taskId, long sequenceIdIgnored) {
        stateManager.publishTaskRollingBackEvent(taskId, "rollback", null);
    }

    @Override
    public void publishTaskRollingBack(String planId, String taskId, java.util.List<String> stagesToRollback, long sequenceIdIgnored) {
        stateManager.publishTaskRollingBackEvent(taskId, "rollback", stagesToRollback);
    }

    @Override
    public void publishTaskStageRollingBack(String planId, String taskId, String stageName, long sequenceIdIgnored) {
        // 可加入单独事件; 暂使用 progress 事件占位
    }

    @Override
    public void publishTaskStageRollbackFailed(String planId, String taskId, String stageName, String reason, long sequenceIdIgnored) {
        FailureInfo fi = new FailureInfo();
        fi.setErrorMessage("rollback failed: " + reason);
        stateManager.publishTaskStageFailedEvent(taskId, stageName, fi);
    }

    @Override
    public void publishTaskRolledBack(String planId, String taskId, long sequenceIdIgnored) {
        stateManager.publishTaskRolledBackEvent(taskId, null);
    }

    @Override
    public void publishTaskRolledBack(String planId, String taskId, java.util.List<String> rolledBackStages, long sequenceIdIgnored) {
        stateManager.publishTaskRolledBackEvent(taskId, rolledBackStages);
    }

    @Override
    public void publishTaskCancelled(String planId, String taskId, long sequenceIdIgnored) {
        stateManager.publishTaskCancelledEvent(taskId);
    }
}
