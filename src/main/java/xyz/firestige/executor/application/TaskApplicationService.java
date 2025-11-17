package xyz.firestige.executor.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.executor.application.dto.TaskOperationResult;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.TaskExecutor;
import xyz.firestige.executor.execution.TaskWorkerFactory;
import xyz.firestige.executor.facade.TaskStatusInfo;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.support.conflict.ConflictRegistry;

import java.util.List;
import java.util.Map;

/**
 * Task 应用服务
 * 负责单个 Task 级别的操作
 *
 * 职责：
 * 1. Task 级别的操作（暂停、恢复、回滚、重试、取消）
 * 2. Task 状态查询
 * 3. 返回 TaskOperationResult 和 TaskStatusInfo
 */
public class TaskApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(TaskApplicationService.class);

    // 核心依赖
    private final TaskStateManager stateManager;
    private final TaskWorkerFactory workerFactory;
    private final ExecutorProperties executorProperties;
    private final CheckpointService checkpointService;
    private final SpringTaskEventSink eventSink;
    private final ConflictRegistry conflictRegistry;

    // 注册表引用（从 PlanApplicationService 共享）
    private final Map<String, TaskAggregate> taskRegistry;
    private final Map<String, TaskRuntimeContext> contextRegistry;
    private final Map<String, List<TaskStage>> stageRegistry;
    private final Map<String, TaskExecutor> executorRegistry;

    public TaskApplicationService(
            TaskStateManager stateManager,
            TaskWorkerFactory workerFactory,
            ExecutorProperties executorProperties,
            CheckpointService checkpointService,
            SpringTaskEventSink eventSink,
            ConflictRegistry conflictRegistry,
            Map<String, TaskAggregate> taskRegistry,
            Map<String, TaskRuntimeContext> contextRegistry,
            Map<String, List<TaskStage>> stageRegistry,
            Map<String, TaskExecutor> executorRegistry) {
        this.stateManager = stateManager;
        this.workerFactory = workerFactory;
        this.executorProperties = executorProperties;
        this.checkpointService = checkpointService;
        this.eventSink = eventSink;
        this.conflictRegistry = conflictRegistry;
        this.taskRegistry = taskRegistry;
        this.contextRegistry = contextRegistry;
        this.stageRegistry = stageRegistry;
        this.executorRegistry = executorRegistry;
    }

    /**
     * 根据租户 ID 暂停任务
     * @param tenantId 租户 ID
     * @return TaskOperationResult
     */
    public TaskOperationResult pauseTaskByTenant(String tenantId) {
        logger.info("[TaskApplicationService] 暂停租户任务: {}", tenantId);

        TaskAggregate target = findTaskByTenantId(tenantId);
        if (target == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        TaskRuntimeContext ctx = contextRegistry.get(target.getTaskId());
        if (ctx != null) {
            ctx.requestPause();
        }

        logger.info("[TaskApplicationService] 租户任务暂停请求已登记: {}", tenantId);
        return TaskOperationResult.success(
            target.getTaskId(),
            TaskStatus.PAUSED,
            "租户任务暂停请求已登记，下一 Stage 生效"
        );
    }

    /**
     * 根据租户 ID 恢复任务
     * @param tenantId 租户 ID
     * @return TaskOperationResult
     */
    public TaskOperationResult resumeTaskByTenant(String tenantId) {
        logger.info("[TaskApplicationService] 恢复租户任务: {}", tenantId);

        TaskAggregate target = findTaskByTenantId(tenantId);
        if (target == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        TaskRuntimeContext ctx = contextRegistry.get(target.getTaskId());
        if (ctx != null) {
            ctx.clearPause();
        }

        logger.info("[TaskApplicationService] 租户任务恢复请求已登记: {}", tenantId);
        return TaskOperationResult.success(
            target.getTaskId(),
            TaskStatus.RUNNING,
            "租户任务恢复请求已登记"
        );
    }

    /**
     * 根据租户 ID 回滚任务
     * @param tenantId 租户 ID
     * @return TaskOperationResult
     */
    public TaskOperationResult rollbackTaskByTenant(String tenantId) {
        logger.info("[TaskApplicationService] 回滚租户任务: {}", tenantId);

        TaskAggregate target = findTaskByTenantId(tenantId);
        if (target == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // 获取或创建 Executor
        TaskExecutor exec = executorRegistry.get(target.getTaskId());
        if (exec == null) {
            List<TaskStage> stages = stageRegistry.getOrDefault(target.getTaskId(), List.of());
            TaskRuntimeContext ctx = contextRegistry.get(target.getTaskId());
            exec = workerFactory.create(
                target.getPlanId(),
                target,
                stages,
                ctx,
                checkpointService,
                eventSink,
                executorProperties.getTaskProgressIntervalSeconds(),
                stateManager,
                conflictRegistry
            );
        }

        // 发布回滚开始事件
        List<String> stageNames = getAndRegStageNames(target);
        eventSink.publishTaskRollingBack(target.getPlanId(), target.getTaskId(), stageNames, 0);

        // 执行回滚
        var res = exec.invokeRollback();

        // 发布回滚结果事件
        if (target.getStatus() == TaskStatus.ROLLED_BACK) {
            eventSink.publishTaskRolledBack(target.getPlanId(), target.getTaskId(), stageNames, 0);
        } else if (target.getStatus() == TaskStatus.ROLLBACK_FAILED) {
            stateManager.publishTaskRollbackFailedEvent(
                target.getTaskId(),
                FailureInfo.of(ErrorType.SYSTEM_ERROR, "rollback failed"),
                null
            );
        }

        logger.info("[TaskApplicationService] 租户任务回滚结束: {}, status: {}", tenantId, res.getFinalStatus());
        return TaskOperationResult.success(
            target.getTaskId(),
            target.getStatus(),
            "租户任务回滚结束: " + res.getFinalStatus()
        );
    }

    /**
     * 根据租户 ID 重试任务
     * @param tenantId 租户 ID
     * @param fromCheckpoint 是否从检查点恢复
     * @return TaskOperationResult
     */
    public TaskOperationResult retryTaskByTenant(String tenantId, boolean fromCheckpoint) {
        logger.info("[TaskApplicationService] 重试租户任务: {}, fromCheckpoint: {}", tenantId, fromCheckpoint);

        TaskAggregate target = findTaskByTenantId(tenantId);
        if (target == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // 获取或创建 Executor
        TaskExecutor exec = executorRegistry.get(target.getTaskId());
        if (exec == null) {
            List<TaskStage> stages = stageRegistry.getOrDefault(target.getTaskId(), List.of());
            TaskRuntimeContext ctx = contextRegistry.get(target.getTaskId());
            exec = workerFactory.create(
                target.getPlanId(),
                target,
                stages,
                ctx,
                checkpointService,
                eventSink,
                executorProperties.getTaskProgressIntervalSeconds(),
                stateManager,
                conflictRegistry
            );
            executorRegistry.put(target.getTaskId(), exec);
        }

        // 补偿进度事件（checkpoint retry）
        if (fromCheckpoint) {
            int completed = target.getCurrentStageIndex();
            int total = stageRegistry.getOrDefault(target.getTaskId(), List.of()).size();
            stateManager.publishTaskProgressEvent(target.getTaskId(), null, completed, total);
        }

        // 执行重试
        var res = exec.retry(fromCheckpoint);

        logger.info("[TaskApplicationService] 租户任务重试启动: {}, status: {}", tenantId, res.getFinalStatus());
        return TaskOperationResult.success(
            target.getTaskId(),
            res.getFinalStatus(),
            "租户任务重试启动"
        );
    }

    /**
     * 查询任务状态
     * @param executionUnitId 执行单 ID
     * @return TaskStatusInfo
     */
    public TaskStatusInfo queryTaskStatus(String executionUnitId) {
        logger.debug("[TaskApplicationService] 查询任务状态: {}", executionUnitId);

        TaskAggregate task = taskRegistry.get(executionUnitId);
        if (task == null) {
            return TaskStatusInfo.failure("任务不存在: " + executionUnitId);
        }

        // 计算进度
        int completed = task.getCurrentStageIndex();
        int total = stageRegistry.getOrDefault(executionUnitId, List.of()).size();
        double progress = total == 0 ? 0 : (completed * 100.0 / total);

        // 获取当前阶段
        TaskExecutor exec = executorRegistry.get(executionUnitId);
        String currentStage = exec != null ? exec.getCurrentStageName() : null;

        // 获取运行时状态
        TaskRuntimeContext ctx = contextRegistry.get(executionUnitId);
        boolean paused = ctx != null && ctx.isPauseRequested();
        boolean cancelled = ctx != null && ctx.isCancelRequested();

        // 构造状态信息
        TaskStatusInfo info = new TaskStatusInfo(executionUnitId, task.getStatus());
        info.setMessage(String.format(
            "进度 %.2f%% (%d/%d), currentStage=%s, paused=%s, cancelled=%s",
            progress, completed, total, currentStage, paused, cancelled
        ));

        return info;
    }

    /**
     * 根据租户 ID 查询任务状态
     * @param tenantId 租户 ID
     * @return TaskStatusInfo
     */
    public TaskStatusInfo queryTaskStatusByTenant(String tenantId) {
        logger.debug("[TaskApplicationService] 查询租户任务状态: {}", tenantId);

        TaskAggregate task = findTaskByTenantId(tenantId);
        if (task == null) {
            return TaskStatusInfo.failure("未找到租户任务: " + tenantId);
        }

        return queryTaskStatus(task.getTaskId());
    }

    /**
     * 取消任务
     * @param executionUnitId 执行单 ID
     * @return TaskOperationResult
     */
    public TaskOperationResult cancelTask(String executionUnitId) {
        logger.info("[TaskApplicationService] 取消任务: {}", executionUnitId);

        TaskAggregate task = taskRegistry.get(executionUnitId);
        if (task == null) {
            return TaskOperationResult.failure(
                executionUnitId,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "任务不存在"),
                "任务不存在"
            );
        }

        // 设置取消标志
        TaskRuntimeContext ctx = contextRegistry.get(task.getTaskId());
        if (ctx != null) {
            ctx.requestCancel();
        }

        // 更新状态
        stateManager.updateState(task.getTaskId(), TaskStatus.CANCELLED);
        stateManager.publishTaskCancelledEvent(task.getTaskId(), "application-service");

        logger.info("[TaskApplicationService] 任务取消请求已登记: {}", executionUnitId);
        return TaskOperationResult.success(
            task.getTaskId(),
            TaskStatus.CANCELLED,
            "任务取消请求已登记"
        );
    }

    /**
     * 根据租户 ID 取消任务
     * @param tenantId 租户 ID
     * @return TaskOperationResult
     */
    public TaskOperationResult cancelTaskByTenant(String tenantId) {
        logger.info("[TaskApplicationService] 取消租户任务: {}", tenantId);

        TaskAggregate task = findTaskByTenantId(tenantId);
        if (task == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        return cancelTask(task.getTaskId());
    }

    // ========== 辅助方法 ==========

    /**
     * 根据租户 ID 查找任务
     */
    private TaskAggregate findTaskByTenantId(String tenantId) {
        return taskRegistry.values().stream()
                .filter(task -> tenantId.equals(task.getTenantId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取并注册 Stage 名称
     */
    private List<String> getAndRegStageNames(TaskAggregate task) {
        List<String> stageNames = stageRegistry.getOrDefault(task.getTaskId(), List.of())
                .stream()
                .map(TaskStage::getName)
                .toList();
        stateManager.registerStageNames(task.getTaskId(), stageNames);
        return stageNames;
    }
}

