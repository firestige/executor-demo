package xyz.firestige.deploy.infrastructure.execution.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskCheckpoint;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * 执行准备器（T-032 优化版）
 * <p>
 * 职责：
 * 根据 Task 当前状态和 Context 请求，完成执行前的准备工作：
 * 1. 状态转换（PENDING→RUNNING, FAILED→RUNNING 等）
 * 2. 确定 Stage 起点（从头开始 or 从检查点恢复）
 * 3. 设置 Context 的执行信息（startIndex + executionMode）
 * <p>
 * 设计理念：
 * - 将"准备"和"执行"分离
 * - 避免重复代码（所有场景复用同一个 executeStages）
 * - 职责单一（只负责准备，不负责执行）
 * - 直接修改 TaskRuntimeContext，不返回额外对象
 *
 * @since T-032 优化版 - 准备器模式
 */
public class ExecutionPreparer {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPreparer.class);

    /**
     * 准备执行
     * <p>
     * 根据 Task 当前状态和 Context 请求，完成状态转换并设置执行信息
     * <p>
     * 执行后，context 的以下字段会被设置：
     * - startIndex: Stage 起点索引
     * - executionMode: 执行模式（NORMAL/ROLLBACK）
     *
     * @param task Task 聚合
     * @param context 运行时上下文（会被修改）
     * @param deps 依赖服务
     * @throws IllegalStateException 如果状态不支持或请求不明确
     */
    public void prepare(
        TaskAggregate task,
        TaskRuntimeContext context,
        ExecutionDependencies deps
    ) {
        TaskStatus currentStatus = task.getStatus();

        log.info("准备执行, taskId: {}, currentStatus: {}, retryRequested: {}, rollbackRequested: {}",
            task.getTaskId(), currentStatus,
            context.isRetryRequested(), context.isRollbackRequested());

        // 根据当前状态选择准备方法
        switch (currentStatus) {
            case PENDING -> preparePendingTask(task, context, deps);
            case PAUSED -> preparePausedTask(task, context, deps);
            case FAILED -> prepareFailedTask(task, context, deps);
            case RUNNING -> prepareRunningTask(task, context, deps);
            default -> throw new IllegalStateException(
                String.format("不支持的状态: %s, taskId: %s", currentStatus, task.getTaskId())
            );
        }

        log.info("准备完成, taskId: {}, startIndex: {}, executionMode: {}",
            task.getTaskId(), context.getStartIndex(), context.getExecutionMode());
    }

    // ========== 准备方法 ==========

    /**
     * 准备 PENDING 任务（首次执行）
     * <p>
     * 状态转换：PENDING → RUNNING
     * Stage 起点：0（从头开始）
     */
    private void preparePendingTask(
        TaskAggregate task,
        TaskRuntimeContext context,
        ExecutionDependencies deps
    ) {
        // 状态转换
        deps.getTaskDomainService().startTask(task, context);

        // 设置执行信息
        context.setStartIndex(0);
        context.setExecutionMode(TaskRuntimeContext.ExecutionMode.NORMAL);
    }

    /**
     * 准备 PAUSED 任务（恢复执行）
     * <p>
     * 状态转换：PAUSED → RUNNING
     * Stage 起点：从检查点恢复
     */
    private void preparePausedTask(
        TaskAggregate task,
        TaskRuntimeContext context,
        ExecutionDependencies deps
    ) {
        // 状态转换
        deps.getTaskDomainService().resumeTask(task, context);

        // 清除暂停标志
        context.clearPause();

        // 设置执行信息
        int startIndex = loadCheckpointStartIndex(task, deps);
        context.setStartIndex(startIndex);
        context.setExecutionMode(TaskRuntimeContext.ExecutionMode.NORMAL);
    }

    /**
     * 准备 FAILED 任务（重试或回滚）
     * <p>
     * 状态转换：
     * - 重试：FAILED → PENDING → RUNNING
     * - 回滚：FAILED → ROLLING_BACK
     * <p>
     * Stage 起点：
     * - 重试：0 或 checkpoint + 1
     * - 回滚：逆序执行
     */
    private void prepareFailedTask(
        TaskAggregate task,
        TaskRuntimeContext context,
        ExecutionDependencies deps
    ) {
        // ========== 🔍 检查点验证：进入 prepare 前 ==========
        log.info("🔍 [Preparer-检查点A] 进入 prepareFailedTask");
        log.info("   - TaskId: {}", task.getTaskId());
        log.info("   - Task.checkpoint: {}", task.getCheckpoint());
        log.info("   - Context.retryRequested: {}", context.isRetryRequested());
        log.info("   - Context.fromCheckpoint: {}", context.isFromCheckpoint());

        // 检查是重试还是回滚
        if (context.isRetryRequested()) {
            // ========== 重试 ==========

            log.info("🔍 [Preparer-检查点B] 调用 retryTask() 前");
            log.info("   - Task Status: {}", task.getStatus());
            log.info("   - Task.checkpoint: {}", task.getCheckpoint());

            // 状态转换：FAILED → PENDING（retry() 方法）
            deps.getTaskDomainService().retryTask(task, context);

            log.info("🔍 [Preparer-检查点C] 调用 retryTask() 后");
            log.info("   - Task Status: {}", task.getStatus());
            log.info("   - Task.checkpoint: {}", task.getCheckpoint());

            // ✅ T-032: retry() 后状态是 PENDING，需要再调用 startTask() → RUNNING
            deps.getTaskDomainService().startTask(task, context);

            log.info("🔍 [Preparer-检查点D] 调用 startTask() 后");
            log.info("   - Task Status: {}", task.getStatus());
            log.info("   - Task.checkpoint: {}", task.getCheckpoint());

            // 确定起点
            if (context.isFromCheckpoint()) {
                log.info("🔍 [Preparer-检查点E] 准备从检查点恢复");

                // T-034: 加载检查点并准备重试范围
                int startIndex = loadCheckpointStartIndex(task, deps);

                // T-034: 准备重试执行范围 [checkpoint+1, totalStages)
                TaskCheckpoint checkpoint = deps.getCheckpointService().loadCheckpoint(task);
                if (checkpoint != null) {
                    task.prepareRetryRange(checkpoint);
                    log.info("✅ [T-034] 准备重试范围: [{}, totalStages)", startIndex);
                }

                log.info("🔍 [Preparer-检查点F] 加载检查点完成");
                log.info("   - startIndex: {}", startIndex);

                context.setStartIndex(startIndex);
            } else {
                log.info("🔍 [Preparer-检查点E'] 从头重试，清空检查点");

                // 从头重试，清空检查点
                deps.getCheckpointService().clearCheckpoint(task);
                context.setStartIndex(0);

                // T-034: 执行范围保持为完整范围（已在 setTotalStages 时设置）
            }

            context.setExecutionMode(TaskRuntimeContext.ExecutionMode.NORMAL);

        } else if (context.isRollbackRequested()) {
            // ========== 回滚 ==========
            // ✅ 回滚 = 使用旧配置重新执行正常流程
            // - 配置来源：prevConfigSnapshot（由 TaskDomainService 准备）
            // - Stage 顺序：正常顺序（不逆序）
            // - 执行逻辑：与正常执行完全相同

            log.info("🔍 [Preparer-Rollback-A] 准备回滚");
            log.info("   - Task Status: {}", task.getStatus());
            log.info("   - Target Version: {}", context.getRollbackTargetVersion());

            // ✅ 设置回滚意图标志（在状态转换之前）
            task.markAsRollbackIntent();
            log.info("✅ 已设置回滚意图标志");

            // 状态转换：FAILED → PENDING（准备重新执行）
            deps.getTaskDomainService().retryTask(task, context);

            log.info("🔍 [Preparer-Rollback-B] retryTask() 后");
            log.info("   - Task Status: {}", task.getStatus());

            // ✅ 再调用 startTask() → RUNNING（此时会发布 TaskRollbackStarted 事件）
            deps.getTaskDomainService().startTask(task, context);

            log.info("🔍 [Preparer-Rollback-C] startTask() 后");
            log.info("   - Task Status: {}", task.getStatus());

            // T-034: 准备回滚执行范围 [0, checkpoint+2)
            TaskCheckpoint checkpoint = deps.getCheckpointService().loadCheckpoint(task);
            if (checkpoint != null) {
                task.prepareRollbackRange(checkpoint);
                int endIndex = checkpoint.getLastCompletedStageIndex() + 2;
                log.info("✅ [T-034] 准备回滚范围: [0, {})", endIndex);
            } else {
                // 没有检查点，从头执行（但这种情况不应该发生在回滚场景）
                log.warn("⚠️ 回滚时没有检查点，将执行全部 Stage");
            }

            // ✅ 回滚从头执行，但保留检查点用于范围判断
            // deps.getCheckpointService().clearCheckpoint(task);  // T-034: 不清空，用于确定范围
            context.setStartIndex(0);

            // ✅ 使用正常模式执行（不是 ROLLBACK 模式）
            context.setExecutionMode(TaskRuntimeContext.ExecutionMode.NORMAL);

            log.info("🔍 [Preparer-Rollback-D] 回滚准备完成");
            log.info("   - startIndex: 0");
            log.info("   - executionMode: NORMAL");
            log.info("   - 配置来源: prevConfigSnapshot");
            log.info("   - 回滚意图标志: true");

        } else {
            // 既没有重试也没有回滚请求
            throw new IllegalStateException(
                String.format("FAILED 状态需要明确是重试还是回滚, taskId: %s", task.getTaskId())
            );
        }
    }


    /**
     * 准备 RUNNING 任务（继续执行，兜底逻辑）
     * <p>
     * 状态转换：无（已经是 RUNNING）
     * Stage 起点：从检查点恢复
     * <p>
     * 注意：这是兜底逻辑，正常情况下不应该走到这里
     */
    private void prepareRunningTask(
        TaskAggregate task,
        TaskRuntimeContext context,
        ExecutionDependencies deps
    ) {
        log.warn("Task 已处于 RUNNING 状态，继续执行, taskId: {}", task.getTaskId());

        // 从检查点恢复
        int startIndex = loadCheckpointStartIndex(task, deps);
        context.setStartIndex(startIndex);
        context.setExecutionMode(TaskRuntimeContext.ExecutionMode.NORMAL);
    }

    // ========== 辅助方法 ==========

    /**
     * 加载检查点并返回起点索引
     */
    private int loadCheckpointStartIndex(TaskAggregate task, ExecutionDependencies deps) {
        log.info("🔍 [LoadCheckpoint-1] 开始加载检查点");
        log.info("   - TaskId: {}", task.getTaskId());

        TaskCheckpoint checkpoint = deps.getCheckpointService().loadCheckpoint(task);

        log.info("🔍 [LoadCheckpoint-2] CheckpointService 返回结果");
        log.info("   - checkpoint: {}", checkpoint);

        if (checkpoint != null) {
            int startIndex = checkpoint.getLastCompletedStageIndex() + 1;
            log.info("🔍 [LoadCheckpoint-3] 从检查点恢复");
            log.info("   - lastCompleted: {}", checkpoint.getLastCompletedStageIndex());
            log.info("   - startIndex: {}", startIndex);
            log.info("   - completedStages: {}", checkpoint.getCompletedStageNames());

            return startIndex;
        } else {
            log.warn("🔍 [LoadCheckpoint-3'] 无检查点，从头开始");
            log.warn("   - TaskId: {}", task.getTaskId());
            log.warn("   - Task.checkpoint: {}", task.getCheckpoint());

            return 0;
        }
    }
}

