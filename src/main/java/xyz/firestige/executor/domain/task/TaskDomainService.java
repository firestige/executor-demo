package xyz.firestige.executor.domain.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.domain.task.TaskOperationResult;
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
 * Task 领域服务 (DDD 重构完成版)
 *
 * 职责（重新定义）：
 * 1. Task 聚合的创建和管理
 * 2. Task 状态管理
 * 3. Task 执行管理
 * 4. 只关注 Task 单聚合的业务逻辑
 *
 * @since DDD 重构 Phase 2.2 - 完成版
 */
public class TaskDomainService {

    private static final Logger logger = LoggerFactory.getLogger(TaskDomainService.class);

    // 核心依赖（DDD 重构后简化）
    private final TaskRepository taskRepository;
    private final TaskStateManager stateManager;
    private final TaskWorkerFactory workerFactory;
    private final ExecutorProperties executorProperties;
    private final CheckpointService checkpointService;
    private final SpringTaskEventSink eventSink;
    private final ConflictRegistry conflictRegistry;

    public TaskDomainService(
            TaskRepository taskRepository,
            TaskStateManager stateManager,
            TaskWorkerFactory workerFactory,
            ExecutorProperties executorProperties,
            CheckpointService checkpointService,
            SpringTaskEventSink eventSink,
            ConflictRegistry conflictRegistry) {
        this.taskRepository = taskRepository;
        this.stateManager = stateManager;
        this.workerFactory = workerFactory;
        this.executorProperties = executorProperties;
        this.checkpointService = checkpointService;
        this.eventSink = eventSink;
        this.conflictRegistry = conflictRegistry;
    }

    /**
     * 创建 Task 聚合（领域服务职责）
     *
     * @param planId Plan ID
     * @param config 租户部署配置
     * @return Task 聚合
     */
    public TaskAggregate createTask(String planId, TenantDeployConfig config) {
        logger.info("[TaskDomainService] 创建 Task: planId={}, tenantId={}", planId, config.getTenantId());

        // 生成 Task ID
        String taskId = generateTaskId(planId, config.getTenantId());

        // 创建 Task 聚合
        TaskAggregate task = new TaskAggregate(taskId, config.getTenantId(), planId);
        task.setStatus(TaskStatus.PENDING);

        // 初始化状态
        stateManager.initializeTask(taskId, TaskStatus.PENDING);

        // 保存到仓储
        taskRepository.save(task);

        logger.info("[TaskDomainService] Task 创建成功: {}", taskId);
        return task;
    }

    /**
     * 构建 Task 的 Stages（需要配置信息）
     *
     * @param task Task 聚合
     * @param config 租户部署配置
     * @param stageFactory Stage 工厂
     * @param healthCheckClient 健康检查客户端
     * @return Stage 列表
     */
    public List<TaskStage> buildTaskStages(
            TaskAggregate task,
            TenantDeployConfig config,
            xyz.firestige.executor.domain.stage.StageFactory stageFactory,
            xyz.firestige.executor.service.health.HealthCheckClient healthCheckClient) {
        logger.debug("[TaskDomainService] 构建 Task Stages: {}", task.getTaskId());

        List<TaskStage> stages = stageFactory.buildStages(task, config, executorProperties, healthCheckClient);

        // 保存到仓储
        taskRepository.saveStages(task.getTaskId(), stages);

        // 注册 Stage 名称
        List<String> stageNames = stages.stream().map(TaskStage::getName).toList();
        stateManager.registerStageNames(task.getTaskId(), stageNames);

        logger.debug("[TaskDomainService] Task Stages 构建完成: {}, stage数量: {}", task.getTaskId(), stages.size());
        return stages;
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

        TaskRuntimeContext ctx = taskRepository.getContext(target.getTaskId());
        if (ctx != null) {
            ctx.requestPause();
        }

        logger.info("[TaskDomainService] 租户任务暂停请求已登记: {}", tenantId);
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
        logger.info("[TaskDomainService] 恢复租户任务: {}", tenantId);

        TaskAggregate target = findTaskByTenantId(tenantId);
        if (target == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        TaskRuntimeContext ctx = taskRepository.getContext(target.getTaskId());
        if (ctx != null) {
            ctx.clearPause();
        }

        logger.info("[TaskDomainService] 租户任务恢复请求已登记: {}", tenantId);
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
        logger.info("[TaskDomainService] 回滚租户任务: {}", tenantId);

        TaskAggregate target = findTaskByTenantId(tenantId);
        if (target == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // 获取或创建 Executor
        TaskExecutor exec = taskRepository.getExecutor(target.getTaskId());
        if (exec == null) {
            List<TaskStage> stages = taskRepository.getStages(target.getTaskId());
            if (stages == null) stages = List.of();
            TaskRuntimeContext ctx = taskRepository.getContext(target.getTaskId());
            // RF-02: 使用 TaskWorkerCreationContext
            exec = workerFactory.create(
                xyz.firestige.executor.execution.TaskWorkerCreationContext.builder()
                    .planId(target.getPlanId())
                    .task(target)
                    .stages(stages)
                    .runtimeContext(ctx)
                    .checkpointService(checkpointService)
                    .eventSink(eventSink)
                    .progressIntervalSeconds(executorProperties.getTaskProgressIntervalSeconds())
                    .stateManager(stateManager)
                    .conflictRegistry(conflictRegistry)
                    .build()
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
        logger.info("[TaskDomainService] 重试租户任务: {}, fromCheckpoint: {}", tenantId, fromCheckpoint);

        TaskAggregate target = findTaskByTenantId(tenantId);
        if (target == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // 获取或创建 Executor
        TaskExecutor exec = taskRepository.getExecutor(target.getTaskId());
        if (exec == null) {
            List<TaskStage> stages = taskRepository.getStages(target.getTaskId());
            if (stages == null) stages = List.of();
            TaskRuntimeContext ctx = taskRepository.getContext(target.getTaskId());
            // RF-02: 使用 TaskWorkerCreationContext
            exec = workerFactory.create(
                xyz.firestige.executor.execution.TaskWorkerCreationContext.builder()
                    .planId(target.getPlanId())
                    .task(target)
                    .stages(stages)
                    .runtimeContext(ctx)
                    .checkpointService(checkpointService)
                    .eventSink(eventSink)
                    .progressIntervalSeconds(executorProperties.getTaskProgressIntervalSeconds())
                    .stateManager(stateManager)
                    .conflictRegistry(conflictRegistry)
                    .build()
            );
            taskRepository.saveExecutor(target.getTaskId(), exec);
        }

        // 补偿进度事件（checkpoint retry）
        if (fromCheckpoint) {
            int completed = target.getCurrentStageIndex();
            List<TaskStage> stages = taskRepository.getStages(target.getTaskId());
            int total = (stages != null) ? stages.size() : 0;
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
        logger.debug("[TaskDomainService] 查询任务状态: {}", executionUnitId);

        TaskAggregate task = taskRepository.get(executionUnitId);
        if (task == null) {
            return TaskStatusInfo.failure("任务不存在: " + executionUnitId);
        }

        // 计算进度
        int completed = task.getCurrentStageIndex();
        List<TaskStage> stages = taskRepository.getStages(executionUnitId);
        int total = (stages != null) ? stages.size() : 0;
        double progress = total == 0 ? 0 : (completed * 100.0 / total);

        // 获取当前阶段
        TaskExecutor exec = taskRepository.getExecutor(executionUnitId);
        String currentStage = exec != null ? exec.getCurrentStageName() : null;

        // 获取运行时状态
        TaskRuntimeContext ctx = taskRepository.getContext(executionUnitId);
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
        logger.info("[TaskDomainService] 取消任务: {}", executionUnitId);

        TaskAggregate task = taskRepository.get(executionUnitId);
        if (task == null) {
            return TaskOperationResult.failure(
                executionUnitId,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "任务不存在"),
                "任务不存在"
            );
        }

        // 设置取消标志
        TaskRuntimeContext ctx = taskRepository.getContext(task.getTaskId());
        if (ctx != null) {
            ctx.requestCancel();
        }

        // 更新状态
        stateManager.updateState(task.getTaskId(), TaskStatus.CANCELLED);
        stateManager.publishTaskCancelledEvent(task.getTaskId(), "domain-service");

        logger.info("[TaskDomainService] 任务取消请求已登记: {}", executionUnitId);
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
     * 生成 Task ID
     */
    private String generateTaskId(String planId, String tenantId) {
        return planId + "_task_" + tenantId + "_" + System.currentTimeMillis();
    }

    /**
     * 根据租户 ID 查找任务
     */
    private TaskAggregate findTaskByTenantId(String tenantId) {
        return taskRepository.findByTenantId(tenantId);
    }

    /**
     * 获取并注册 Stage 名称
     */
    private List<String> getAndRegStageNames(TaskAggregate task) {
        List<TaskStage> stages = taskRepository.getStages(task.getTaskId());
        List<String> stageNames = (stages != null)
            ? stages.stream().map(TaskStage::getName).toList()
            : List.of();
        stateManager.registerStageNames(task.getTaskId(), stageNames);
        return stageNames;
    }
}

