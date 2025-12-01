package xyz.firestige.deploy.infrastructure.execution.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * 执行准备器（T-035 无状态版）
 * <p>
 * 职责：
 * 根据 Task 当前状态和 Context 请求，完成执行前的准备工作：
 * 1. 状态转换（PENDING→RUNNING）
 * 2. 设置 Context 的执行信息（保持已设置的 startIndex + executionMode）
 * <p>
 * T-035 简化：
 * - 移除 Checkpoint 机制
 * - PAUSED/FAILED 场景由 TaskRecoveryService 重建后进入 PENDING 状态
 * - ExecutionPreparer 只处理 PENDING 和 RUNNING 状态
 *
 * @since T-035 无状态执行器
 */
public class ExecutionPreparer {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPreparer.class);

    /**
     * 准备执行
     * <p>
     * 根据 Task 当前状态和 Context 请求，完成状态转换并设置执行信息
     * <p>
     * T-035: startIndex 和 ExecutionRange 已由 TaskRecoveryService 设置（恢复场景）
     * 或在 TaskAggregate 创建时设置（首次执行）
     *
     * @param task Task 聚合
     * @param context 运行时上下文（会被修改）
     * @param deps 依赖服务
     * @throws IllegalStateException 如果状态不支持
     */
    public void prepare(
        TaskAggregate task,
        TaskRuntimeContext context,
        ExecutionDependencies deps
    ) {
        TaskStatus currentStatus = task.getStatus();

        log.info("准备执行, taskId: {}, currentStatus: {}, startIndex: {}",
            task.getTaskId(), currentStatus, context.getStartIndex());

        // 根据当前状态选择准备方法
        switch (currentStatus) {
            case PENDING -> preparePendingTask(task, context, deps);
            case RUNNING -> prepareRunningTask(task, context, deps);
            default -> throw new IllegalStateException(
                String.format("不支持的状态: %s, taskId: %s (PAUSED/FAILED 场景应先通过 TaskRecoveryService 重建为 PENDING)",
                    currentStatus, task.getTaskId())
            );
        }

        log.info("准备完成, taskId: {}, startIndex: {}, executionMode: {}",
            task.getTaskId(), context.getStartIndex(), context.getExecutionMode());
    }

    // ========== 准备方法 ==========

    /**
     * 准备 PENDING 任务
     * <p>
     * 状态转换：PENDING → RUNNING
     * <p>
     * T-035: startIndex 和 ExecutionRange 已在外部设置：
     * - 首次执行：TaskAggregate 创建时设置 startIndex=0
     * - 恢复执行：TaskRecoveryService 根据 lastCompletedStageName 计算后设置
     */
    private void preparePendingTask(
        TaskAggregate task,
        TaskRuntimeContext context,
        ExecutionDependencies deps
    ) {
        // 状态转换
        deps.getTaskDomainService().startTask(task, context);

        // T-035: startIndex 和 executionMode 已在外部设置，无需修改
        // 首次执行时由 TaskAggregate 构造函数设置为 0
        // 恢复场景由 TaskRecoveryService 设置为计算值
    }

    /**
     * 准备 RUNNING 任务（运行中暂停后恢复）
     * <p>
     * T-035: 仅处理内存中 Task 还在的场景（pause → resume）
     * 状态转换：无（已经是 RUNNING）
     */
    private void prepareRunningTask(
        TaskAggregate task,
        TaskRuntimeContext context,
        ExecutionDependencies deps
    ) {
        // RUNNING 状态下恢复执行（例如暂停后恢复）
        if (context.isPauseRequested()) {
            throw new IllegalStateException(
                String.format("RUNNING 任务不能暂停自己, taskId: %s", task.getTaskId())
            );
        }

        // 清除暂停标志（如果有）
        context.clearPause();

        // T-035: startIndex 保持当前进度（StageProgress 中维护）
        log.info("RUNNING 任务恢复执行, taskId: {}, 从当前进度继续", task.getTaskId());
    }
}

