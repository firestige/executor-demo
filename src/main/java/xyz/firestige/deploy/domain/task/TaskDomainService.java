package xyz.firestige.deploy.domain.task;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.facade.TaskStatusInfo;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerCreationContext;
import xyz.firestige.deploy.infrastructure.state.TaskStateManager;

/**
 * Task 领域服务 (RF-15: 执行层解耦版)
 * <p>
 * 职责（RF-15 重构）：
 * 1. Task 聚合的创建和生命周期管理
 * 2. Task 业务状态转换（pause、resume、cancel）
 * 3. 为执行操作准备聚合和上下文数据
 * 4. 只关注领域逻辑，不涉及执行器创建和调度
 * <p>
 * 改进点：
 * - 移除了 TaskWorkerFactory、CheckpointService、ExecutorProperties、TenantConflictManager
 * - rollback/retry 方法改为准备方法，返回 TaskExecutionContext
 * - 应用层负责创建和执行 TaskExecutor
 *
 * @since RF-15: TaskDomainService 执行层解耦
 */
public class TaskDomainService {

    private static final Logger logger = LoggerFactory.getLogger(TaskDomainService.class);

    // 核心依赖（RF-15: 移除执行层依赖）
    private final TaskRepository taskRepository;
    private final TaskRuntimeRepository taskRuntimeRepository;
    private final TaskStateManager stateManager;
    // ✅ RF-11: 使用领域事件发布器接口（支持多种实现）
    private final DomainEventPublisher domainEventPublisher;

    public TaskDomainService(
            TaskRepository taskRepository,
            TaskRuntimeRepository taskRuntimeRepository,
            TaskStateManager stateManager,
            DomainEventPublisher domainEventPublisher) {
        this.taskRepository = taskRepository;
        this.taskRuntimeRepository = taskRuntimeRepository;
        this.stateManager = stateManager;
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
     * 准备回滚任务（RF-17: 返回简化的 TaskWorkerCreationContext）
     * 应用层负责创建 TaskExecutor 并执行回滚
     *
     * @param tenantId 租户 ID
     * @return TaskWorkerCreationContext 包含执行所需的聚合和运行时数据，null 表示未找到任务
     */
    public TaskWorkerCreationContext prepareRollbackByTenant(String tenantId) {
        logger.info("[TaskDomainService] 准备回滚租户任务: {}", tenantId);

        TaskAggregate target = findTaskByTenantId(tenantId);
        if (target == null) {
            logger.warn("[TaskDomainService] 未找到租户任务: {}", tenantId);
            return null;
        }

        // 发布回滚开始事件
        domainEventPublisher.publishAll(target.getDomainEvents());
        target.clearDomainEvents();

        // 获取运行时数据
        List<TaskStage> stages = taskRuntimeRepository.getStages(target.getTaskId()).orElseGet(List::of);
        TaskRuntimeContext ctx = taskRuntimeRepository.getContext(target.getTaskId()).orElse(null);
        TaskExecutor executor = taskRuntimeRepository.getExecutor(target.getTaskId()).orElse(null);

        logger.info("[TaskDomainService] 任务准备完成，等待应用层执行回滚: {}", target.getTaskId());
        return TaskWorkerCreationContext.builder()
            .planId(target.getPlanId())
            .task(target)
            .stages(stages)
            .runtimeContext(ctx)
            .existingExecutor(executor)
            .build();
    }

    /**
     * 准备重试任务（RF-17: 返回简化的 TaskWorkerCreationContext）
     * 应用层负责创建 TaskExecutor 并执行重试
     *
     * @param tenantId 租户 ID
     * @param fromCheckpoint 是否从检查点恢复
     * @return TaskWorkerCreationContext 包含执行所需的聚合和运行时数据，null 表示未找到任务
     */
    public TaskWorkerCreationContext prepareRetryByTenant(String tenantId, boolean fromCheckpoint) {
        logger.info("[TaskDomainService] 准备重试租户任务: {}, fromCheckpoint: {}", tenantId, fromCheckpoint);

        TaskAggregate target = findTaskByTenantId(tenantId);
        if (target == null) {
            logger.warn("[TaskDomainService] 未找到租户任务: {}", tenantId);
            return null;
        }

        // 补偿进度事件（checkpoint retry）
        if (fromCheckpoint) {
            int completed = target.getCurrentStageIndex();
            int total = taskRuntimeRepository.getStages(target.getTaskId()).orElseGet(List::of).size();
            // 发布进度补偿事件
        }

        // 获取运行时数据
        List<TaskStage> stages = taskRuntimeRepository.getStages(target.getTaskId()).orElseGet(List::of);
        TaskRuntimeContext ctx = taskRuntimeRepository.getContext(target.getTaskId()).orElse(null);
        TaskExecutor executor = taskRuntimeRepository.getExecutor(target.getTaskId()).orElse(null);

        logger.info("[TaskDomainService] 任务准备完成，等待应用层执行重试: {}", target.getTaskId());
        return TaskWorkerCreationContext.builder()
            .planId(target.getPlanId())
            .task(target)
            .stages(stages)
            .runtimeContext(ctx)
            .existingExecutor(executor)
            .build();
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

        task.cancel("任务取消请求已登记");// 调用聚合的取消方法
        taskRepository.save(task);// 保存状态变更

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

}

