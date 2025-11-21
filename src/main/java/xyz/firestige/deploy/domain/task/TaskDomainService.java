package xyz.firestige.deploy.domain.task;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.event.TaskCreatedEvent;
import xyz.firestige.deploy.domain.task.event.TaskRetryStartedEvent;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.facade.TaskStatusInfo;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerCreationContext;

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

    // 核心依赖（RF-18: 方案C架构）
    private final TaskRepository taskRepository;
    private final TaskRuntimeRepository taskRuntimeRepository;
    private final StateTransitionService stateTransitionService;  // ✅ 状态转换服务（依赖倒置）
    private final DomainEventPublisher domainEventPublisher;

    public TaskDomainService(
            TaskRepository taskRepository,
            TaskRuntimeRepository taskRuntimeRepository,
            StateTransitionService stateTransitionService,
            DomainEventPublisher domainEventPublisher) {
        this.taskRepository = taskRepository;
        this.taskRuntimeRepository = taskRuntimeRepository;
        this.stateTransitionService = stateTransitionService;
        this.domainEventPublisher = domainEventPublisher;
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
        TaskCreatedEvent createdEvent = new TaskCreatedEvent(TaskInfo.from(task), names);
        domainEventPublisher.publish(createdEvent);

        logger.debug("[TaskDomainService] Task Stages 构建完成: {}, stage数量: {}", task.getTaskId(), stages.size());
    }

    // ========== 方案C: 执行生命周期方法（封装save+publish逻辑）==========

    /**
     * 启动任务（内部前置检查）
     */
    public void startTask(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 启动任务: {}", task.getTaskId());
        
        if (!stateTransitionService.canTransition(task, TaskStatus.RUNNING, context)) {
            throw new IllegalStateException("任务当前状态不允许启动: " + task.getStatus());
        }
        
        task.start();
        saveAndPublishEvents(task);
    }

    /**
     * 恢复任务
     */
    public void resumeTask(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 恢复任务: {}", task.getTaskId());
        
        if (!stateTransitionService.canTransition(task, TaskStatus.RUNNING, context)) {
            throw new IllegalStateException("任务当前状态不允许恢复: " + task.getStatus());
        }
        
        task.resume();
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
     */
    public void pauseTask(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 暂停任务: {}", task.getTaskId());
        
        if (!stateTransitionService.canTransition(task, TaskStatus.PAUSED, context)) {
            throw new IllegalStateException("任务当前状态不允许暂停: " + task.getStatus());
        }
        
        task.applyPauseAtStageBoundary();
        saveAndPublishEvents(task);
    }

    /**
     * 取消任务
     */
    public void cancelTask(TaskAggregate task, String reason, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 取消任务: {}, reason: {}", task.getTaskId(), reason);
        
        if (!stateTransitionService.canTransition(task, TaskStatus.CANCELLED, context)) {
            throw new IllegalStateException("任务当前状态不允许取消: " + task.getStatus());
        }
        
        task.cancel(reason);
        saveAndPublishEvents(task);
    }

    /**
     * 完成任务
     */
    public void completeTask(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 完成任务: {}", task.getTaskId());
        
        if (!stateTransitionService.canTransition(task, TaskStatus.COMPLETED, context)) {
            throw new IllegalStateException("任务当前状态不允许完成: " + task.getStatus());
        }
        
        task.complete();
        saveAndPublishEvents(task);
    }

    /**
     * 开始回滚
     */
    public void startRollback(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 开始回滚: {}", task.getTaskId());
        
        if (!stateTransitionService.canTransition(task, TaskStatus.ROLLING_BACK, context)) {
            throw new IllegalStateException("任务当前状态不允许回滚: " + task.getStatus());
        }
        
        task.rollback();
        saveAndPublishEvents(task);
    }

    /**
     * 回滚完成
     */
    public void completeRollback(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 回滚完成: {}", task.getTaskId());
        
        task.completeRollback();
        saveAndPublishEvents(task);
    }

    /**
     * 回滚失败
     */
    public void failRollback(TaskAggregate task, FailureInfo failure, TaskRuntimeContext context) {
        logger.error("[TaskDomainService] 回滚失败: {}, reason: {}", task.getTaskId(), failure.getErrorMessage());
        
        task.failRollback(failure.getErrorMessage());
        saveAndPublishEvents(task);
    }

    /**
     * 重试任务
     */
    public void retryTask(TaskAggregate task, TaskRuntimeContext context) {
        logger.info("[TaskDomainService] 重试任务: {}", task.getTaskId());
        
        if (!stateTransitionService.canTransition(task, TaskStatus.RUNNING, context)) {
            throw new IllegalStateException("任务当前状态不允许重试: " + task.getStatus());
        }
        
        task.retry();
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
     * 准备回滚任务（RF-17: 返回简化的 TaskWorkerCreationContext）
     * 应用层负责创建 TaskExecutor 并执行回滚
     *
     * @param tenantId 租户 ID
     * @return TaskWorkerCreationContext 包含执行所需的聚合和运行时数据，null 表示未找到任务
     */
    public TaskWorkerCreationContext prepareRollbackByTenant(TenantId tenantId) {
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
    public TaskWorkerCreationContext prepareRetryByTenant(TenantId tenantId, boolean fromCheckpoint) {
        logger.info("[TaskDomainService] 准备重试租户任务: {}, fromCheckpoint: {}", tenantId, fromCheckpoint);

        TaskAggregate target = findTaskByTenantId(tenantId);
        if (target == null) {
            logger.warn("[TaskDomainService] 未找到租户任务: {}", tenantId);
            return null;
        }

        // 补偿进度事件（checkpoint retry）
        if (fromCheckpoint && target.getCheckpoint() != null) {
            int completed = target.getCurrentStageIndex();
            List<TaskStage> stages = taskRuntimeRepository.getStages(target.getTaskId()).orElseGet(List::of);
            int total = stages.size();
            
            // ✅ 发布进度补偿事件（告知监控系统从检查点恢复）
            TaskRetryStartedEvent retryEvent = new TaskRetryStartedEvent(TaskInfo.from(target), true);
            domainEventPublisher.publish(retryEvent);
            
            logger.info("[TaskDomainService] 已发布检查点恢复进度事件: taskId={}, progress={}/{}", 
                target.getTaskId(), completed, total);
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

