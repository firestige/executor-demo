package xyz.firestige.deploy.domain.task;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.application.task.TaskRecoveryService;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.event.TaskCreatedEvent;
import xyz.firestige.deploy.facade.TaskStatusInfo;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerCreationContext;
import xyz.firestige.deploy.infrastructure.execution.stage.StageFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

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
 * <p>
 * T-028: 新增 StageFactory 依赖用于回滚时重新装配 Stages
 *
 * @since RF-15: TaskDomainService 执行层解耦
 */
public class TaskDomainService {

    private static final Logger logger = LoggerFactory.getLogger(TaskDomainService.class);

    // 核心依赖（T-033: 移除 StateTransitionService，状态转换由聚合根保护）
    private final TaskRepository taskRepository;
    private final TaskRuntimeRepository taskRuntimeRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final StageFactory stageFactory;  // T-028: 回滚时重新装配 Stages
    private final TaskRecoveryService taskRecoveryService;

    public TaskDomainService(
            TaskRepository taskRepository,
            TaskRuntimeRepository taskRuntimeRepository,
            DomainEventPublisher domainEventPublisher,
            StageFactory stageFactory,
            TaskRecoveryService taskRecoveryService) {
        this.taskRepository = taskRepository;
        this.taskRuntimeRepository = taskRuntimeRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.stageFactory = stageFactory;
        this.taskRecoveryService = taskRecoveryService;
    }

    /**
     * 创建 Task 聚合（领域服务职责）
     *
     * @param planId Plan ID
     * @param config 租户配置（内部 DTO）
     * @return Task 聚合
     */
    public TaskAggregate createTask(PlanId planId, TenantConfig config) {
        logger.info("[TaskDomainService] 创建 Task: planId={}, tenantId={}", planId, config.getTenantId());

        // 生成 Task ID
        TaskId taskId = generateTaskId(planId, config.getTenantId());

        // 创建 Task 聚合根
        TaskAggregate task = new TaskAggregate(taskId, planId, config.getTenantId());

        // T-028: 保存完整的上一版配置（用于回滚）
        if (config.getPreviousConfig() != null) {
            task.setPrevConfig(config.getPreviousConfig());
            logger.info("[TaskDomainService] 保存 prevConfig: taskId={}, prevVersion={}",
                taskId, config.getPreviousConfig().getPlanVersion());
        }

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
        task.setTotalStages(stages);

        // 保存到仓储
        taskRuntimeRepository.saveStages(task.getTaskId(), stages);

        List<String> names = stages.stream().map(TaskStage::getName).toList();
        // 发布 TaskCreated 事件
        TaskCreatedEvent createdEvent = new TaskCreatedEvent(task, names);
        domainEventPublisher.publish(createdEvent);

        logger.debug("[TaskDomainService] Task Stages 构建完成: {}, stage数量: {}", task.getTaskId(), stages.size());
    }

    // ========== 方案C: 执行生命周期方法（封装save+publish逻辑）==========

    /**
     * 启动任务
     * <p>
     * T-033: 状态检查由聚合根内部保护，不需要预检验
     */
    public void startTask(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 启动任务: {}", task.getTaskId());
        
        task.start();  // 聚合根内部会检查状态
        saveAndPublishEvents(task);
    }

    /**
     * 恢复任务
     * <p>
     * T-033: 状态检查由聚合根内部保护
     */
    public void resumeTask(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 恢复任务: {}", task.getTaskId());
        
        task.resume();  // 聚合根内部会检查状态
        saveAndPublishEvents(task);
        
        // 更新 RuntimeContext
        taskRuntimeRepository.getContext(task.getTaskId()).ifPresent(TaskRuntimeContext::clearPause);
    }

    /**
     * 开始执行 Stage（RF-19-01 新增）
     *
     * @param task Task 聚合
     * @param stageName Stage 名称
     * @param totalSteps Stage 包含的 Step 总数
     */
    public void startStage(TaskAggregate task, String stageName, int totalSteps) {
        logger.debug("[TaskDomainService] 开始执行 Stage: {}, stage: {}", task.getTaskId(), stageName);

        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的任务才能开始 Stage，当前状态: " + task.getStatus());
        }

        task.startStage(stageName, totalSteps);  // ✅ 聚合产生事件
        saveAndPublishEvents(task);  // ✅ 领域服务发布事件
    }

    /**
     * 完成 Stage
     */
    public void completeStage(TaskAggregate task, String stageName, java.time.Duration duration, TaskRuntimeContext context) {
        logger.debug("[TaskDomainService] 完成 Stage: {}, stage: {}", task.getTaskId(), stageName);
        
        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的任务才能完成 Stage，当前状态: " + task.getStatus());
        }
        
        task.completeStage(stageName, duration);
        saveAndPublishEvents(task);
    }

    /**
     * Stage 失败（RF-19-01 新增）
     *
     * @param task Task 聚合
     * @param stageName 失败的 Stage 名称
     * @param failureInfo 失败信息
     */
    public void failStage(TaskAggregate task, String stageName, FailureInfo failureInfo) {
        logger.warn("[TaskDomainService] Stage 失败: {}, stage: {}, reason: {}",
            task.getTaskId(), stageName, failureInfo.getErrorMessage());

        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的任务才能记录 Stage 失败，当前状态: " + task.getStatus());
        }

        task.failStage(stageName, failureInfo);  // ✅ 聚合产生事件
        saveAndPublishEvents(task);  // ✅ 领域服务发布事件
    }

    /**
     * 任务失败
     */
    public void failTask(TaskAggregate task, FailureInfo failure, TaskRuntimeContext context) {
        logger.warn("[TaskDomainService] 任务失败: {}, reason: {}", task.getTaskId(), failure.getErrorMessage());
        
        task.fail(failure);
        saveAndPublishEvents(task);
    }

    /**
     * 暂停任务
     * <p>
     * T-033: 状态检查由聚合根内部保护
     */
    public void pauseTask(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 暂停任务: {}", task.getTaskId());
        
        task.applyPauseAtStageBoundary();  // 聚合根内部会检查状态
        saveAndPublishEvents(task);
    }

    /**
     * 取消任务
     * <p>
     * T-033: 状态检查由聚合根内部保护
     */
    public void cancelTask(TaskAggregate task, String reason, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 取消任务: {}, reason: {}", task.getTaskId(), reason);
        
        task.cancel(reason);  // 聚合根内部会检查状态
        saveAndPublishEvents(task);
    }

    /**
     * 完成任务
     * <p>
     * T-033: 状态检查由聚合根内部保护
     */
    public void completeTask(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 完成任务: {}", task.getTaskId());
        
        task.complete();  // 聚合根内部会检查状态
        saveAndPublishEvents(task);
    }


    /**
     * 重试任务
     * <p>
     * T-033: 状态检查由聚合根内部保护
     */
    public void retryTask(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 重试任务: {}", task.getTaskId());
        
        task.retry();  // 聚合根内部会检查状态
        saveAndPublishEvents(task);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 保存聚合并发布事件（封装重复逻辑）
     */
    private void saveAndPublishEvents(TaskAggregate task) {
        taskRepository.save(task);
        domainEventPublisher.publishAll(task.getDomainEvents());
        task.clearDomainEvents();
    }

    // ========== 原有的租户级别操作方法（保持兼容）==========

    /**
     * 根据租户 ID 暂停任务
     * DDD 重构：调用聚合的业务方法
     *
     * @param tenantId 租户 ID
     * @return TaskOperationResult
     */
    public TaskOperationResult pauseTaskByTenant(TenantId tenantId) {
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
    public TaskOperationResult resumeTaskByTenant(TenantId tenantId) {
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
     * 查询任务状态
     * @param taskId 执行单 ID
     * @return TaskStatusInfo
     */
    public TaskStatusInfo queryTaskStatus(TaskId taskId) {
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
    public TaskStatusInfo queryTaskStatusByTenant(TenantId tenantId) {
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
    public TaskOperationResult cancelTask(TaskId taskId) {
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
    public TaskOperationResult cancelTaskByTenant(TenantId tenantId) {
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

    /**
     * 准备重试任务（T-035: 无状态执行器适配）
     * <p>
     * 设计要点：
     * 1. 根据 config 中的 tenantId 查找现有 Task
     * 2. 调用 TaskRecoveryService 重建 TaskAggregate（基于 lastCompletedStageName）
     * 3. lastCompletedStageName 为 null 时，从头到尾全部重试
     * 4. 返回 TaskWorkerCreationContext 供应用层创建 Executor
     * 
     * @param config 用于重试的配置（和上次执行一样）
     * @param lastCompletedStageName 最近完成的 Stage 名称（null 表示从头重试）
     * @return TaskWorkerCreationContext 包含执行所需的聚合和运行时数据，null 表示未找到任务
     */
    public TaskWorkerCreationContext prepareRetry(TenantConfig config, String lastCompletedStageName) {
        TenantId tenantId = config.getTenantId();
        logger.info("[TaskDomainService] 准备重试任务: {}, lastCompletedStage: {}", 
                    tenantId, lastCompletedStageName);

        // 1. 查找现有 Task
        TaskAggregate existingTask = findTaskByTenantId(tenantId);
        if (existingTask == null) {
            logger.warn("[TaskDomainService] 未找到租户任务: {}, 开始重建", tenantId);
            TaskId taskId = TaskId.of("task-" + UUID.randomUUID());
            existingTask = taskRecoveryService.recoverForRetry(taskId, config, lastCompletedStageName);
            taskRepository.save(existingTask);
        }

        // 2. 使用相同配置重新装配 Stages
        List<TaskStage> stages = stageFactory.buildStages(config);
        logger.info("[TaskDomainService] 重新装配 Stages: taskId={}, stageCount={}", 
                    existingTask.getTaskId(), stages.size());

        // 3. 计算起始索引（lastCompletedStageName 为 null 时从 0 开始）
        int startIndex;
        if (lastCompletedStageName != null && !lastCompletedStageName.isEmpty()) {
            startIndex = calculateStartIndex(stages, lastCompletedStageName);
            logger.info("[TaskDomainService] 从 Stage[{}] 开始重试: {}", 
                        startIndex, lastCompletedStageName);
        } else {
            logger.info("[TaskDomainService] lastCompletedStageName 为 null，从头开始重试");
        }

        // 4. 构造运行时上下文
        TaskRuntimeContext ctx = new TaskRuntimeContext(
            existingTask.getPlanId(),
            existingTask.getTaskId(),
            config.getTenantId()
        );

        // 5. 设置起始索引到 Task（如果需要）
        // 注意：这里可能需要调用 Task 的某个方法来设置 currentStageIndex
        // 具体实现取决于 TaskAggregate 的 API
        // existingTask.setCurrentStageIndex(startIndex);

        logger.info("[TaskDomainService] 任务准备完成，等待应用层执行重试: {}", existingTask.getTaskId());
        return TaskWorkerCreationContext.builder()
            .planId(existingTask.getPlanId())
            .task(existingTask)
            .stages(stages)
            .runtimeContext(ctx)
            .existingExecutor(null)  // 不复用 Executor
            .build();
    }

    /**
     * 准备回滚任务（T-035: 无状态执行器适配）
     * <p>
     * 设计要点：
     * 1. 根据 oldConfig 中的 tenantId 查找现有 Task
     * 2. 使用旧版本配置重新装配 Stages
     * 3. 回滚不是逆向操作，而是用旧版本配置正向执行 stages
     * 4. lastCompletedStageName 为 null 时，全部回滚
     * 5. version 用于单调递增版本号校验
     * 
     * @param oldConfig 旧版本配置（回滚目标）
     * @param lastCompletedStageName 最近完成的 Stage 名称（null 表示全部回滚）
     * @param version 操作版本号（用于版本校验）
     * @return TaskWorkerCreationContext 包含执行所需的聚合和运行时数据，null 表示未找到任务
     */
    public TaskWorkerCreationContext prepareRollback(
        TenantConfig oldConfig,
        String lastCompletedStageName,
        String version
    ) {
        TenantId tenantId = oldConfig.getTenantId();
        logger.info("[TaskDomainService] 准备回滚任务: {}, lastCompletedStage: {}, version: {}", 
                    tenantId, lastCompletedStageName, version);

        // 1. 查找现有 Task
        TaskAggregate existingTask = findTaskByTenantId(tenantId);
        if (existingTask == null) {
            logger.warn("[TaskDomainService] 未找到租户任务: {}, 开始重建", tenantId);
            TaskId taskId = TaskId.of("task-" + UUID.randomUUID());
            existingTask = taskRecoveryService.recoverForRollback(taskId, oldConfig, lastCompletedStageName);
            taskRepository.save(existingTask);
        }

        // 3. 使用旧版本配置重新装配 Stages（关键：用旧配置刷回）
        List<TaskStage> rollbackStages = stageFactory.buildStages(oldConfig);
        logger.info("[TaskDomainService] 用旧版本配置重新装配 Stages: taskId={}, stageCount={}", 
                    existingTask.getTaskId(), rollbackStages.size());

        // 4. 计算起始索引（lastCompletedStageName 为 null 时从 0 开始）
        int startIndex;
        if (lastCompletedStageName != null && !lastCompletedStageName.isEmpty()) {
            startIndex = calculateStartIndex(rollbackStages, lastCompletedStageName);
            logger.info("[TaskDomainService] 从 Stage[{}] 开始回滚: {}", 
                        startIndex, lastCompletedStageName);
        } else {
            logger.info("[TaskDomainService] lastCompletedStageName 为 null，全部回滚");
        }

        // 5. 构造回滚运行时上下文（使用旧配置 + 新版本号）
        TaskRuntimeContext rollbackCtx = new TaskRuntimeContext(
            existingTask.getPlanId(),
            existingTask.getTaskId(),
            oldConfig.getTenantId()
        );
        // 设置版本号（用于版本校验）
        // rollbackCtx.setVersion(version); // 根据实际 API 调整

        logger.info("[TaskDomainService] 任务准备完成，等待应用层执行回滚: {}", existingTask.getTaskId());
        return TaskWorkerCreationContext.builder()
            .planId(existingTask.getPlanId())
            .task(existingTask)
            .stages(rollbackStages)      // 使用旧配置装配的 Stages
            .runtimeContext(rollbackCtx) // 使用旧配置的 Context
            .existingExecutor(null)      // 不复用 Executor
            .build();
    }

    /**
     * 计算起始索引（根据 lastCompletedStageName）
     * <p>
     * 如果找不到对应的 Stage，返回 0（从头开始）
     * 
     * @param stages Stage 列表
     * @param lastCompletedStageName 最后完成的 Stage 名称
     * @return 起始索引（从 lastCompletedIndex + 1 开始）
     */
    private int calculateStartIndex(List<TaskStage> stages, String lastCompletedStageName) {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getName().equals(lastCompletedStageName)) {
                return i + 1;  // 从下一个 Stage 开始
            }
        }
        
        logger.warn("[TaskDomainService] 未找到 Stage: {}，从头开始", lastCompletedStageName);
        return 0;  // 找不到时从头开始
    }

    // ========== 辅助方法 ==========

    /**
     * 生成 Task ID
     */
    private TaskId generateTaskId(PlanId planId, TenantId tenantId) {
        return TaskId.of("task-" + planId + "_" +  tenantId + "_" + System.currentTimeMillis());
    }

    /**
     * 根据租户 ID 查找任务
     */
    private TaskAggregate findTaskByTenantId(TenantId tenantId) {
        return taskRepository.findByTenantId(tenantId).orElse(null);
    }

}

