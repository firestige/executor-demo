package xyz.firestige.deploy.application.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.ExecutionRange;
import xyz.firestige.deploy.domain.task.StageProgress;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.infrastructure.execution.stage.StageFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

import java.util.List;

/**
 * 任务恢复服务（T-035 无状态执行器）
 * <p>
 * 职责：
 * 1. 从外部传入的关键信息重建 Task 聚合
 * 2. 计算恢复执行的起始位置和范围
 * 3. 支持 Retry 和 Rollback 两种恢复场景
 * <p>
 * 设计说明：
 * - 执行器不持久化状态，由调用方监听事件并持久化
 * - 恢复时调用方提供：taskId + TenantConfig + lastCompletedStageName
 * - TaskRecoveryService 根据这些信息重建 Task 并设置执行范围
 * <p>
 * 数据流：
 * 1. StageFactory.buildStages(config) → 重建 Stage 列表
 * 2. StageFactory.calculateStartIndex(config, lastCompletedStageName) → 计算索引
 * 3. TaskAggregate.createForRecovery(taskId, ...) → 创建 Task
 * 4. task.setStageProgress() + task.setExecutionRange() → 设置状态
 *
 * @since T-035 无状态执行器
 */
@Service
public class TaskRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(TaskRecoveryService.class);

    private final StageFactory stageFactory;

    public TaskRecoveryService(StageFactory stageFactory) {
        this.stageFactory = stageFactory;
    }

    /**
     * 从失败/完成状态恢复并重试
     * <p>
     * 场景：Task 已 COMPLETED/FAILED，需要重试执行
     * <p>
     * 执行范围：[lastCompletedIndex + 1, totalStages)
     *
     * @param taskId 复用的 taskId
     * @param config 租户配置（用于重建 Stage）
     * @param lastCompletedStageName 最后完成的 Stage 名称
     * @return 重建的 TaskAggregate（状态 PENDING）
     */
    public TaskAggregate recoverForRetry(
            TaskId taskId,
            TenantConfig config,
            String lastCompletedStageName) {

        log.info("恢复任务用于重试, taskId: {}, tenant: {}, lastCompletedStage: {}",
            taskId, config.getTenantId(), lastCompletedStageName);

        // Step 1: 重建 Stage 列表（幂等）
        List<TaskStage> stages = stageFactory.buildStages(config);
        log.info("重建了 {} 个 stages", stages.size());

        // Step 2: 计算最后完成的 Stage 索引
        int lastCompletedIndex = stageFactory.calculateStartIndex(config, lastCompletedStageName);
        log.info("计算得到 lastCompletedIndex: {}", lastCompletedIndex);

        // Step 3: 计算重试起点（lastCompletedIndex + 1）
        int retryStartIndex = lastCompletedIndex + 1;
        if (retryStartIndex >= stages.size()) {
            throw new IllegalArgumentException(
                String.format("无法重试：lastCompletedIndex=%d 已是最后一个 Stage，总共 %d 个 stages",
                    lastCompletedIndex, stages.size())
            );
        }

        // Step 4: 创建 TaskAggregate（复用 taskId，PENDING 状态）
        TaskAggregate task = TaskAggregate.createForRecovery(
            taskId,
            config.getPlanId(),
            config.getTenantId(),
            false  // rollbackIntent = false
        );

        // Step 5: 设置 StageProgress（从 retryStartIndex 开始）
        StageProgress progress = StageProgress.of(retryStartIndex, stages);
        task.setStageProgress(progress);

        // Step 6: 设置 ExecutionRange [retryStartIndex, totalStages)
        ExecutionRange range = ExecutionRange.forRetry(retryStartIndex, stages.size());
        task.setExecutionRange(range);

        log.info("重试恢复完成, taskId: {}, startIndex: {}, range: [{}, {})",
            taskId, retryStartIndex, range.getStartIndex(), range.getEndIndex());

        return task;
    }

    /**
     * 从失败/完成状态恢复并回滚
     * <p>
     * 场景：Task 已 COMPLETED/FAILED，需要用旧配置回滚
     * <p>
     * 执行范围：[0, lastCompletedIndex + 1)
     *
     * @param taskId 复用的 taskId
     * @param oldConfig 旧的租户配置（用于回滚）
     * @param lastCompletedStageName 最后完成的 Stage 名称
     * @return 重建的 TaskAggregate（状态 PENDING，rollbackIntent = true）
     */
    public TaskAggregate recoverForRollback(
            TaskId taskId,
            TenantConfig oldConfig,
            String lastCompletedStageName) {

        log.info("恢复任务用于回滚, taskId: {}, tenant: {}, lastCompletedStage: {}",
            taskId, oldConfig.getTenantId(), lastCompletedStageName);

        // Step 1: 用旧配置重建 Stage 列表（幂等）
        List<TaskStage> stages = stageFactory.buildStages(oldConfig);
        log.info("用旧配置重建了 {} 个 stages", stages.size());

        // Step 2: 计算最后完成的 Stage 索引
        int lastCompletedIndex = stageFactory.calculateStartIndex(oldConfig, lastCompletedStageName);
        log.info("计算得到 lastCompletedIndex: {}", lastCompletedIndex);

        // Step 3: 计算回滚终点（lastCompletedIndex + 1）
        int rollbackEndIndex = lastCompletedIndex + 1;

        // Step 4: 创建 TaskAggregate（复用 taskId，PENDING 状态，回滚模式）
        TaskAggregate task = TaskAggregate.createForRecovery(
            taskId,
            oldConfig.getPlanId(),
            oldConfig.getTenantId(),
            true  // rollbackIntent = true
        );

        // Step 5: 设置 StageProgress（从 0 开始）
        StageProgress progress = StageProgress.of(0, stages);
        task.setStageProgress(progress);

        // Step 6: 设置 ExecutionRange [0, rollbackEndIndex)
        ExecutionRange range = ExecutionRange.forRollback(rollbackEndIndex);
        task.setExecutionRange(range);

        log.info("回滚恢复完成, taskId: {}, startIndex: 0, range: [0, {})",
            taskId, rollbackEndIndex);

        return task;
    }
}
