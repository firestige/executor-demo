package xyz.firestige.deploy.domain.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.event.DomainEventPublisher;
import xyz.firestige.deploy.checkpoint.CheckpointService;
import xyz.firestige.deploy.config.ExecutorProperties;
import xyz.firestige.deploy.domain.stage.TaskStage;
import xyz.firestige.deploy.event.SpringTaskEventSink;
import xyz.firestige.deploy.exception.ErrorType;
import xyz.firestige.deploy.exception.FailureInfo;
import xyz.firestige.deploy.execution.TaskExecutor;
import xyz.firestige.deploy.execution.TaskWorkerCreationContext;
import xyz.firestige.deploy.execution.TaskWorkerFactory;
import xyz.firestige.deploy.facade.TaskStatusInfo;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.state.TaskStatus;
import xyz.firestige.deploy.support.conflict.ConflictRegistry;

import java.util.List;

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

    // 核心依赖（DDD 重构后简化 + RF-09 添加 RuntimeRepository）
    private final TaskRepository taskRepository;
    private final TaskRuntimeRepository taskRuntimeRepository;
    private final TaskStateManager stateManager;
    private final TaskWorkerFactory workerFactory;
    private final ExecutorProperties executorProperties;
    private final CheckpointService checkpointService;
    private final ConflictRegistry conflictRegistry;
    // ✅ RF-11 改进版: 使用领域事件发布器接口（支持多种实现）
    private final DomainEventPublisher domainEventPublisher;

    public TaskDomainService(
            TaskRepository taskRepository,
            TaskRuntimeRepository taskRuntimeRepository,
            TaskStateManager stateManager,
            TaskWorkerFactory workerFactory,
            ExecutorProperties executorProperties,
            CheckpointService checkpointService,
            ConflictRegistry conflictRegistry,
            DomainEventPublisher domainEventPublisher) {
        this.taskRepository = taskRepository;
        this.taskRuntimeRepository = taskRuntimeRepository;
        this.stateManager = stateManager;
        this.workerFactory = workerFactory;
        this.executorProperties = executorProperties;
        this.checkpointService = checkpointService;
        this.conflictRegistry = conflictRegistry;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 创建 Task 聚合（领域服务职责）
     *
     * @param planId Plan ID
     * @param config 租户配置（内部 DTO）
     * @return Task 聚合
     */
    public TaskAggregate createTask(String planId, TenantConfig config) {
        logger.info("[TaskDomainService] 创建 Task: planId={}, tenantId={}", planId, config.getTenantId());

        // 生成 Task ID
        String taskId = generateTaskId(planId, config.getTenantId());

        // 创建 Task 聚合
        TaskAggregate task = new TaskAggregate(taskId, config.getTenantId(), planId);
        // ✅ 调用聚合的业务方法
        task.markAsPending();

        // 初始化状态
        stateManager.initializeTask(taskId, TaskStatus.PENDING);

        // 保存到仓储
        taskRepository.save(task);

        // ✅ RF-11: 提取并发布聚合产生的领域事件
        domainEventPublisher.publishAll(task.getDomainEvents());
        task.clearDomainEvents();

        logger.info("[TaskDomainService] Task 创建成功: {}", taskId);
        return task;
    }

    /**
     * 构建 Task 的 Stages（需要配置信息）
     *
     * @param task Task 聚合
     * @param stages Stage 列表
     */
    public void attacheStages(
            TaskAggregate task,
            List<TaskStage> stages) {
        logger.debug("[TaskDomainService] 构建 Task Stages: {}", task.getTaskId());

        // 保存到仓储
        taskRuntimeRepository.saveStages(task.getTaskId(), stages);

        // 注册 Stage 名称
        List<String> stageNames = stages.stream().map(TaskStage::getName).toList();
        stateManager.registerStageNames(task.getTaskId(), stageNames);

        logger.debug("[TaskDomainService] Task Stages 构建完成: {}, stage数量: {}", task.getTaskId(), stages.size());
    }

    /**
     * 根据租户 ID 暂停任务
     * DDD 重构：调用聚合的业务方法
     *
     * @param tenantId 租户 ID
     * @return TaskOperationResult
     */
    public TaskOperationResult pauseTaskByTenant(String tenantId) {
        logger.info("[TaskDomainService] 暂停租户任务: {}", tenantId);

        TaskAggregate target = findTaskByTenantId(tenantId);
        if (target == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        try {
            // ✅ 调用聚合的业务方法（不变式保护在聚合内部）
            target.requestPause();
            taskRepository.save(target);

            // ✅ RF-11: 提取并发布聚合产生的领域事件
            domainEventPublisher.publishAll(target.getDomainEvents());
            target.clearDomainEvents();

            // 更新 RuntimeContext（用于执行器检查）
            taskRuntimeRepository.getContext(target.getTaskId()).ifPresent(TaskRuntimeContext::requestPause);

            logger.info("[TaskDomainService] 租户任务暂停请求已登记: {}", tenantId);
            return TaskOperationResult.success(
                target.getTaskId(),
                target.getStatus(),
                "租户任务暂停请求已登记，下一 Stage 生效"
            );
        } catch (IllegalStateException e) {
            logger.warn("[TaskDomainService] 暂停请求失败: {}", e.getMessage());
            return TaskOperationResult.failure(
                target.getTaskId(),
                FailureInfo.of(ErrorType.VALIDATION_ERROR, e.getMessage()),
                "暂停失败: " + e.getMessage()
            );
        }
    }

    /**
     * 根据租户 ID 恢复任务
     * DDD 重构：调用聚合的业务方法
     *
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

        try {
            // ✅ 调用聚合的业务方法（不变式保护在聚合内部）
            target.resume();
            taskRepository.save(target);

            // ✅ RF-11: 提取并发布聚合产生的领域事件
            domainEventPublisher.publishAll(target.getDomainEvents());
            target.clearDomainEvents();

            // 更新 RuntimeContext
            taskRuntimeRepository.getContext(target.getTaskId()).ifPresent(TaskRuntimeContext::clearPause);

            logger.info("[TaskDomainService] 租户任务已恢复: {}", tenantId);
            return TaskOperationResult.success(
                target.getTaskId(),
                target.getStatus(),
                "租户任务已恢复"
            );
        } catch (IllegalStateException e) {
            logger.warn("[TaskDomainService] 恢复失败: {}", e.getMessage());
            return TaskOperationResult.failure(
                target.getTaskId(),
                FailureInfo.of(ErrorType.VALIDATION_ERROR, e.getMessage()),
                "恢复失败: " + e.getMessage()
            );
        }
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
        TaskExecutor exec = taskRuntimeRepository.getExecutor(target.getTaskId()).orElseGet(() -> {
            List<TaskStage> stages = taskRuntimeRepository.getStages(target.getTaskId()).orElseGet(List::of);
            // 由于上面对租户进行查询，所以下面的 getContext 一定存在，不应该抛异常
            TaskRuntimeContext ctx = taskRuntimeRepository.getContext(target.getTaskId()).orElseThrow();
            return getTaskExecutor(target, stages, ctx);
        });

        // 发布回滚开始事件
        domainEventPublisher.publishAll(target.getDomainEvents());

        // 执行回滚
        var res = exec.invokeRollback();

        // 发布回滚结果事件
        if (target.getStatus() == TaskStatus.ROLLED_BACK) {
            domainEventPublisher.publishAll(target.getDomainEvents());
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
        TaskExecutor exec = taskRuntimeRepository.getExecutor(target.getTaskId()).orElseGet(() -> {
            List<TaskStage> stages = taskRuntimeRepository.getStages(target.getTaskId()).orElseGet(List::of);
            // 由于上面对租户进行查询，所以下面的 getContext 一定存在，不应该抛异常
            TaskRuntimeContext ctx = taskRuntimeRepository.getContext(target.getTaskId()).orElseThrow();
            TaskExecutor exec0 = getTaskExecutor(target, stages, ctx);
            taskRuntimeRepository.saveExecutor(target.getTaskId(), exec0);
            return exec0;
        });

        // 补偿进度事件（checkpoint retry）
        if (fromCheckpoint) {
            int completed = target.getCurrentStageIndex();
            int total = taskRuntimeRepository.getStages(target.getTaskId()).orElseGet(List::of).size();
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
     * @param taskId 执行单 ID
     * @return TaskStatusInfo
     */
    public TaskStatusInfo queryTaskStatus(String taskId) {
        logger.debug("[TaskDomainService] 查询任务状态: {}", taskId);

        TaskAggregate task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return TaskStatusInfo.failure("任务不存在: " + taskId);
        }

        // 计算进度
        int completed = task.getCurrentStageIndex();
        List<TaskStage> stages = taskRuntimeRepository.getStages(taskId).orElse(null);
        int total = (stages != null) ? stages.size() : 0;
        double progress = total == 0 ? 0 : (completed * 100.0 / total);

        // 获取当前阶段
        TaskExecutor exec = taskRuntimeRepository.getExecutor(taskId).orElse(null);
        String currentStage = exec != null ? exec.getCurrentStageName() : null;

        // 获取运行时状态
        TaskRuntimeContext ctx = taskRuntimeRepository.getContext(taskId).orElse(null);
        boolean paused = ctx != null && ctx.isPauseRequested();
        boolean cancelled = ctx != null && ctx.isCancelRequested();

        // 构造状态信息
        TaskStatusInfo info = new TaskStatusInfo(taskId, task.getStatus());
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
     * @param taskId 执行单 ID
     * @return TaskOperationResult
     */
    public TaskOperationResult cancelTask(String taskId) {
        logger.info("[TaskDomainService] 取消任务: {}", taskId);

        TaskAggregate task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return TaskOperationResult.failure(
                    taskId,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "任务不存在"),
                "任务不存在"
            );
        }

        // 设置取消标志
        taskRuntimeRepository.getContext(task.getTaskId()).ifPresent(TaskRuntimeContext::requestCancel);

        // 更新状态
        stateManager.updateState(task.getTaskId(), TaskStatus.CANCELLED);
        stateManager.publishTaskCancelledEvent(task.getTaskId(), "domain-service");

        logger.info("[TaskDomainService] 任务取消请求已登记: {}", taskId);
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
        return taskRepository.findByTenantId(tenantId).orElse(null);
    }

    /**
     * 获取并注册 Stage 名称
     */
    private List<String> getAndRegStageNames(TaskAggregate task) {
        List<String> stageNames = taskRuntimeRepository.getStages(task.getTaskId())
                .map(list -> list.stream().map(TaskStage::getName).toList())
                .orElseGet(List::of);
        stateManager.registerStageNames(task.getTaskId(), stageNames);
        return stageNames;
    }


    private TaskExecutor getTaskExecutor(TaskAggregate target, List<TaskStage> stages, TaskRuntimeContext ctx) {
        TaskExecutor exec;
        exec = workerFactory.create(
                TaskWorkerCreationContext.builder()
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
        return exec;
    }
}

