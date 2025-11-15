package xyz.firestige.executor.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.exception.ExecutionException;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.pipeline.Pipeline;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户任务执行器
 * 负责执行单个租户的蓝绿切换任务
 */
@Deprecated
public class TenantTaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TenantTaskExecutor.class);

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 租户部署配置
     */
    private TenantDeployConfig tenantDeployConfig;

    /**
     * Pipeline
     */
    private Pipeline pipeline;

    /**
     * Pipeline 上下文
     */
    private PipelineContext context;

    /**
     * 状态管理器
     */
    private TaskStateManager stateManager;

    /**
     * 执行开始时间
     */
    private LocalDateTime startTime;

    /**
     * 执行结束时间
     */
    private LocalDateTime endTime;

    public TenantTaskExecutor(String taskId, TenantDeployConfig tenantDeployConfig,
                              Pipeline pipeline, TaskStateManager stateManager) {
        this.taskId = taskId;
        this.tenantDeployConfig = tenantDeployConfig;
        this.pipeline = pipeline;
        this.stateManager = stateManager;
        this.context = new PipelineContext(taskId, tenantDeployConfig);
    }

    /**
     * 执行任务
     */
    public PipelineResult execute() {
        logger.info("开始执行租户任务: taskId={}, tenantId={}", taskId, tenantDeployConfig.getTenantId());

        startTime = LocalDateTime.now();

        try {
            // 发布任务开始事件
            if (stateManager != null) {
                int totalStages = pipeline.getStages().size();
                stateManager.publishTaskStartedEvent(taskId, totalStages);
                stateManager.updateState(taskId, TaskStatus.RUNNING);
            }

            // 执行 Pipeline
            PipelineResult result = pipeline.execute(context);

            endTime = LocalDateTime.now();
            Duration duration = Duration.between(startTime, endTime);

            // 根据结果更新状态
            if (result.isSuccess()) {
                logger.info("租户任务执行成功: taskId={}, tenantId={}, 耗时={}ms",
                        taskId, tenantDeployConfig.getTenantId(), duration.toMillis());

                if (stateManager != null) {
                    List<String> completedStages = result.getCompletedStages().stream()
                            .map(StageResult::getStageName)
                            .collect(Collectors.toList());
                    stateManager.publishTaskCompletedEvent(taskId, duration, completedStages);
                    stateManager.updateState(taskId, TaskStatus.COMPLETED);
                }
            } else {
                logger.error("租户任务执行失败: taskId={}, tenantId={}, 失败原因: {}",
                        taskId, tenantDeployConfig.getTenantId(),
                        result.getFailureInfo() != null ? result.getFailureInfo().getErrorMessage() : "未知");

                if (stateManager != null) {
                    List<String> completedStages = result.getCompletedStages().stream()
                            .map(StageResult::getStageName)
                            .collect(Collectors.toList());
                    String failedStage = result.getFailedStage() != null ? result.getFailedStage().getStageName() : null;

                    stateManager.publishTaskFailedEvent(taskId, result.getFailureInfo(), completedStages, failedStage);
                    stateManager.updateState(taskId, TaskStatus.FAILED, result.getFailureInfo());
                }
            }

            return result;

        } catch (Exception e) {
            logger.error("租户任务执行异常: taskId={}, tenantId={}", taskId, tenantDeployConfig.getTenantId(), e);

            endTime = LocalDateTime.now();

            FailureInfo failureInfo = FailureInfo.fromException(e, xyz.firestige.executor.exception.ErrorType.SYSTEM_ERROR, "TenantTaskExecutor");

            if (stateManager != null) {
                stateManager.publishTaskFailedEvent(taskId, failureInfo, null, null);
                stateManager.updateState(taskId, TaskStatus.FAILED, failureInfo);
            }

            return PipelineResult.failure(null, null, failureInfo);
        }
    }

    /**
     * 暂停任务
     */
    public void pause() {
        logger.info("暂停租户任务: taskId={}, tenantId={}", taskId, tenantDeployConfig.getTenantId());

        context.requestPause();

        if (stateManager != null) {
            stateManager.publishTaskPausedEvent(taskId, "system", context.getCurrentStage());
            stateManager.updateState(taskId, TaskStatus.PAUSED);
        }
    }

    /**
     * 恢复任务
     */
    public PipelineResult resume() {
        logger.info("恢复租户任务: taskId={}, tenantId={}", taskId, tenantDeployConfig.getTenantId());

        context.resume();

        if (stateManager != null) {
            stateManager.publishTaskResumedEvent(taskId, "system", context.getCurrentStage());
            stateManager.updateState(taskId, TaskStatus.RESUMING);
        }

        startTime = LocalDateTime.now();

        // 从检查点恢复执行
        PipelineResult result = pipeline.resumeFromCheckpoint(context);

        endTime = LocalDateTime.now();

        // 更新最终状态
        if (result.isSuccess() && stateManager != null) {
            Duration duration = Duration.between(startTime, endTime);
            List<String> completedStages = result.getCompletedStages().stream()
                    .map(StageResult::getStageName)
                    .collect(Collectors.toList());
            stateManager.publishTaskCompletedEvent(taskId, duration, completedStages);
            stateManager.updateState(taskId, TaskStatus.COMPLETED);
        } else if (!result.isSuccess() && stateManager != null) {
            List<String> completedStages = result.getCompletedStages().stream()
                    .map(StageResult::getStageName)
                    .collect(Collectors.toList());
            String failedStage = result.getFailedStage() != null ? result.getFailedStage().getStageName() : null;

            stateManager.publishTaskFailedEvent(taskId, result.getFailureInfo(), completedStages, failedStage);
            stateManager.updateState(taskId, TaskStatus.FAILED, result.getFailureInfo());
        }

        return result;
    }

    /**
     * 回滚任务
     */
    public void rollback() {
        logger.info("回滚租户任务: taskId={}, tenantId={}", taskId, tenantDeployConfig.getTenantId());

        try {
            if (stateManager != null) {
                List<String> stagesToRollback = pipeline.getStages().stream()
                        .map(stage -> stage.getName())
                        .collect(Collectors.toList());
                stateManager.publishTaskRollingBackEvent(taskId, "manual rollback", stagesToRollback);
                stateManager.updateState(taskId, TaskStatus.ROLLING_BACK);
            }

            // 执行回滚
            pipeline.rollback(context);

            logger.info("租户任务回滚成功: taskId={}, tenantId={}", taskId, tenantDeployConfig.getTenantId());

            if (stateManager != null) {
                List<String> rolledBackStages = pipeline.getStages().stream()
                        .map(stage -> stage.getName())
                        .collect(Collectors.toList());
                stateManager.publishTaskRolledBackEvent(taskId, rolledBackStages);
                stateManager.updateState(taskId, TaskStatus.ROLLED_BACK);
            }

        } catch (Exception e) {
            logger.error("租户任务回滚失败: taskId={}, tenantId={}", taskId, tenantDeployConfig.getTenantId(), e);

            FailureInfo failureInfo = FailureInfo.fromException(e, xyz.firestige.executor.exception.ErrorType.SYSTEM_ERROR, "Rollback");

            if (stateManager != null) {
                stateManager.publishTaskRollbackFailedEvent(taskId, failureInfo, null);
                stateManager.updateState(taskId, TaskStatus.ROLLBACK_FAILED, failureInfo);
            }

            throw new ExecutionException(failureInfo);
        }
    }

    /**
     * 重试任务
     */
    public PipelineResult retry(boolean fromCheckpoint) {
        logger.info("重试租户任务: taskId={}, tenantId={}, fromCheckpoint={}",
                taskId, tenantDeployConfig.getTenantId(), fromCheckpoint);

        if (fromCheckpoint) {
            return resume();
        } else {
            // 清除检查点，从头开始
            if (pipeline.getCheckpointManager() != null) {
                pipeline.getCheckpointManager().clearCheckpoint(taskId);
            }
            return execute();
        }
    }

    /**
     * 取消任务
     */
    public void cancel() {
        logger.info("取消租户任务: taskId={}, tenantId={}", taskId, tenantDeployConfig.getTenantId());
        context.requestCancel();

        if (stateManager != null) {
            stateManager.updateState(taskId, TaskStatus.CANCELLED);
        }
    }

    /**
     * 获取执行耗时
     */
    public Duration getDuration() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        return Duration.between(startTime, end);
    }

    // Getters and Setters

    public String getTaskId() {
        return taskId;
    }

    public TenantDeployConfig getTenantDeployConfig() {
        return tenantDeployConfig;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public PipelineContext getContext() {
        return context;
    }

    public TaskStateManager getStateManager() {
        return stateManager;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }
}

