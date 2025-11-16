package xyz.firestige.executor.execution.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.PipelineResult;
import xyz.firestige.executor.execution.StageResult;
import xyz.firestige.executor.execution.StageStatus;
import xyz.firestige.executor.execution.checkpoint.Checkpoint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pipeline 管道
 * 按顺序执行多个 Stage
 */
public class Pipeline {

    private static final Logger logger = LoggerFactory.getLogger(Pipeline.class);

    /**
     * Stage 列表（按 order 排序）
     */
    private List<PipelineStage> stages;

    /**
     * 检查点管理器
     */
    private CheckpointManager checkpointManager;

    public Pipeline() {
        this.stages = new ArrayList<>();
    }

    public Pipeline(List<PipelineStage> stages) {
        this.stages = new ArrayList<>(stages);
        this.stages.sort(Comparator.comparingInt(PipelineStage::getOrder));
    }

    /**
     * 添加 Stage
     */
    public Pipeline addStage(PipelineStage stage) {
        this.stages.add(stage);
        this.stages.sort(Comparator.comparingInt(PipelineStage::getOrder));
        return this;
    }

    /**
     * 执行 Pipeline
     */
    public PipelineResult execute(PipelineContext context) {
        List<StageResult> completedStages = new ArrayList<>();
        LocalDateTime startTime = LocalDateTime.now();

        try {
            for (PipelineStage stage : stages) {
                // 检查是否应该暂停
                if (context.shouldPause()) {
                    logger.info("Pipeline 暂停，当前 Stage: {}", stage.getName());
                    // 保存检查点
                    if (checkpointManager != null) {
                        saveCheckpoint(context, stage.getName(), completedStages);
                    }
                    return PipelineResult.success(completedStages);
                }

                // 检查是否应该取消
                if (context.shouldCancel()) {
                    logger.info("Pipeline 取消，当前 Stage: {}", stage.getName());
                    return PipelineResult.success(completedStages);
                }

                // 检查是否可以跳过
                if (stage.canSkip(context)) {
                    logger.info("跳过 Stage: {}", stage.getName());
                    StageResult skippedResult = StageResult.skipped(stage.getName(), "条件不满足，跳过执行");
                    completedStages.add(skippedResult);
                    continue;
                }

                // 更新当前 Stage
                context.setCurrentStage(stage.getName());

                // 执行 Stage
                logger.info("开始执行 Stage: {}", stage.getName());
                StageResult stageResult = executeStage(stage, context);
                completedStages.add(stageResult);

                // 如果 Stage 失败，停止执行
                if (!stageResult.isSuccess()) {
                    logger.error("Stage 执行失败: {}, 错误: {}", stage.getName(),
                            stageResult.getFailureInfo() != null ? stageResult.getFailureInfo().getErrorMessage() : "未知错误");

                    FailureInfo failureInfo = stageResult.getFailureInfo();
                    if (failureInfo == null) {
                        failureInfo = FailureInfo.of(ErrorType.SYSTEM_ERROR, "Stage 执行失败", stage.getName());
                    }

                    return PipelineResult.failure(completedStages, stageResult, failureInfo);
                }

                // 保存检查点
                if (checkpointManager != null) {
                    saveCheckpoint(context, stage.getName(), completedStages);
                }

                logger.info("Stage 执行成功: {}", stage.getName());
            }

            // 所有 Stage 执行完成
            PipelineResult result = PipelineResult.success(completedStages);
            result.calculateTotalDuration();
            return result;

        } catch (Exception e) {
            logger.error("Pipeline 执行异常", e);

            FailureInfo failureInfo = FailureInfo.fromException(
                    e,
                    ErrorType.SYSTEM_ERROR,
                    context.getCurrentStage()
            );

            StageResult failedResult = StageResult.failure(context.getCurrentStage(), failureInfo);
            return PipelineResult.failure(completedStages, failedResult, failureInfo);
        }
    }

    /**
     * 从检查点恢复执行
     */
    public PipelineResult resumeFromCheckpoint(PipelineContext context) {
        if (checkpointManager == null) {
            logger.warn("CheckpointManager 未配置，无法从检查点恢复");
            return execute(context);
        }

        // 加载检查点
        Checkpoint checkpoint =
                checkpointManager.loadCheckpoint(context.getTaskId());

        if (checkpoint == null) {
            logger.info("未找到检查点，从头开始执行");
            return execute(context);
        }

        logger.info("从检查点恢复，上次执行到 Stage: {}", checkpoint.getStageName());

        // 恢复上下文数据
        context.setCheckpointData(checkpoint.getStageData().getOrDefault(checkpoint.getStageName(), new java.util.HashMap<>()));

        // 找到恢复点
        int resumeIndex = -1;
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getName().equals(checkpoint.getStageName())) {
                resumeIndex = i + 1; // 从下一个 Stage 开始
                break;
            }
        }

        if (resumeIndex == -1 || resumeIndex >= stages.size()) {
            logger.info("已执行完所有 Stage，无需继续");
            return PipelineResult.success(new ArrayList<>());
        }

        // 执行剩余的 Stage
        List<StageResult> completedStages = new ArrayList<>();

        for (int i = resumeIndex; i < stages.size(); i++) {
            PipelineStage stage = stages.get(i);

            if (context.shouldPause() || context.shouldCancel()) {
                break;
            }

            context.setCurrentStage(stage.getName());

            logger.info("恢复执行 Stage: {}", stage.getName());
            StageResult stageResult = executeStage(stage, context);
            completedStages.add(stageResult);

            if (!stageResult.isSuccess()) {
                FailureInfo failureInfo = stageResult.getFailureInfo();
                return PipelineResult.failure(completedStages, stageResult, failureInfo);
            }

            if (checkpointManager != null) {
                saveCheckpoint(context, stage.getName(), completedStages);
            }
        }

        PipelineResult result = PipelineResult.success(completedStages);
        result.calculateTotalDuration();
        return result;
    }

    /**
     * 回滚 Pipeline
     * 按逆序调用已执行 Stage 的 rollback 方法
     */
    public void rollback(PipelineContext context) {
        if (checkpointManager == null) {
            logger.warn("CheckpointManager 未配置，无法确定回滚范围");
            return;
        }

        // 加载检查点
        Checkpoint checkpoint =
                checkpointManager.loadCheckpoint(context.getTaskId());

        if (checkpoint == null) {
            logger.info("未找到检查点，无需回滚");
            return;
        }

        // 找到需要回滚的 Stage
        List<PipelineStage> stagesToRollback = new ArrayList<>();
        for (PipelineStage stage : stages) {
            if (stage.supportsRollback() && checkpoint.hasStageData(stage.getName())) {
                stagesToRollback.add(stage);
            }
            if (stage.getName().equals(checkpoint.getStageName())) {
                break;
            }
        }

        // 逆序回滚
        logger.info("开始回滚，需要回滚 {} 个 Stage", stagesToRollback.size());
        for (int i = stagesToRollback.size() - 1; i >= 0; i--) {
            PipelineStage stage = stagesToRollback.get(i);

            try {
                logger.info("回滚 Stage: {}", stage.getName());
                stage.rollback(context);
                logger.info("Stage 回滚成功: {}", stage.getName());
            } catch (Exception e) {
                logger.error("Stage 回滚失败: {}, 错误: {}", stage.getName(), e.getMessage(), e);
                // 继续回滚其他 Stage
            }
        }

        logger.info("回滚完成");
    }

    /**
     * 执行单个 Stage
     */
    private StageResult executeStage(PipelineStage stage, PipelineContext context) {
        StageResult result = new StageResult();
        result.setStageName(stage.getName());
        result.setStartTime(LocalDateTime.now());

        try {
            StageResult stageResult = stage.execute(context);
            result.setStatus(stageResult.getStatus());
            result.setSuccess(stageResult.isSuccess());
            result.setOutput(stageResult.getOutput());
            result.setFailureInfo(stageResult.getFailureInfo());
        } catch (Exception e) {
            logger.error("Stage 执行异常: {}", stage.getName(), e);
            result.setStatus(StageStatus.FAILED);
            result.setSuccess(false);

            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, stage.getName());
            result.setFailureInfo(failureInfo);
        }

        result.setEndTime(LocalDateTime.now());
        result.calculateDuration();

        return result;
    }

    /**
     * 保存检查点
     */
    private void saveCheckpoint(PipelineContext context, String stageName, List<StageResult> completedStages) {
        try {
            Checkpoint checkpoint =
                    new Checkpoint(context.getTaskId(), stageName);

            // 保存每个 Stage 的输出数据
            for (StageResult stageResult : completedStages) {
                checkpoint.saveStageData(stageResult.getStageName(), stageResult.getOutput());
            }

            checkpointManager.saveCheckpoint(context.getTaskId(), stageName, checkpoint);
        } catch (Exception e) {
            logger.error("保存检查点失败: {}", e.getMessage(), e);
        }
    }

    // Getters and Setters

    public List<PipelineStage> getStages() {
        return stages;
    }

    public void setStages(List<PipelineStage> stages) {
        this.stages = stages;
        this.stages.sort(Comparator.comparingInt(PipelineStage::getOrder));
    }

    public CheckpointManager getCheckpointManager() {
        return checkpointManager;
    }

    public void setCheckpointManager(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }
}

