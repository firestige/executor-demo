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
import xyz.firestige.deploy.infrastructure.execution.stage.StageFactory;

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

    public TaskDomainService(
            TaskRepository taskRepository,
            TaskRuntimeRepository taskRuntimeRepository,
            DomainEventPublisher domainEventPublisher,
            StageFactory stageFactory) {
        this.taskRepository = taskRepository;
        this.taskRuntimeRepository = taskRuntimeRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.stageFactory = stageFactory;
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
     * 准备回滚任务（T-028: 用 prevConfig + planVersion 重新装配 Stages）
     * 应用层负责创建 TaskExecutor 并执行回滚
     *
     * @param tenantId 租户 ID
     * @param planVersion 回滚目标版本（用于 RedisAck 的 metadata.version、footprint 验证）
     * @return TaskWorkerCreationContext 包含执行所需的聚合和运行时数据，null 表示未找到任务或无法回滚
     */
    public TaskWorkerCreationContext prepareRollbackByTenant(TenantId tenantId, String planVersion) {
        logger.info("[TaskDomainService] 准备回滚租户任务: {}, planVersion: {}", tenantId, planVersion);

        TaskAggregate task = findTaskByTenantId(tenantId);
        if (task == null) {
            logger.warn("[TaskDomainService] 未找到租户任务: {}", tenantId);
            return null;
        }

        // 1. 检查前置条件：必须有 prevConfig
        TenantConfig prevConfig = task.getPrevConfig();
        if (prevConfig == null) {
            logger.warn("[TaskDomainService] Task {} 无 prevConfig，无法回滚", task.getTaskId());
            return null;
        }

        // 2. 检查状态是否允许回滚
        TaskStatus status = task.getStatus();
        if (status != TaskStatus.FAILED && status != TaskStatus.PAUSED) {
            logger.warn("[TaskDomainService] Task {} 状态 {} 不允许回滚", task.getTaskId(), status);
            return null;
        }

        // 3. 构造回滚配置：用 prevConfig + 新的 planVersion
        TenantConfig rollbackConfig = buildRollbackConfig(prevConfig, planVersion);
        logger.info("[TaskDomainService] 构造回滚配置: taskId={}, prevPlanVersion={}, rollbackPlanVersion={}",
            task.getTaskId(), prevConfig.getPlanVersion(), rollbackConfig.getPlanVersion());

        // 4. 用回滚配置重新装配 Stages（关键：DataPreparer 会捕获 rollbackConfig）
        List<TaskStage> rollbackStages = stageFactory.buildStages(rollbackConfig);
        logger.info("[TaskDomainService] 用回滚配置重新装配 Stages: taskId={}, stageCount={}",
            task.getTaskId(), rollbackStages.size());

        // 5. 构造回滚 RuntimeContext（装填旧配置数据 + 新的 planVersion）
        TaskRuntimeContext rollbackCtx = buildRollbackContext(task, rollbackConfig);
        logger.info("[TaskDomainService] 构造回滚 RuntimeContext: taskId={}, planVersion={}",
            task.getTaskId(), planVersion);

        // 6. 发布回滚开始事件
        domainEventPublisher.publishAll(task.getDomainEvents());
        task.clearDomainEvents();

        // 7. 返回执行上下文（不复用原有 Executor）
        logger.info("[TaskDomainService] 任务准备完成，等待应用层执行回滚: {}", task.getTaskId());
        return TaskWorkerCreationContext.builder()
            .planId(task.getPlanId())
            .task(task)
            .stages(rollbackStages)           // ← 使用回滚配置装配的 Stages
            .runtimeContext(rollbackCtx)      // ← 使用回滚配置装填的 Context
            .existingExecutor(null)           // ← 不复用 Executor
            .build();
    }

    /**
     * T-028: 构造回滚配置（基于 prevConfig + 新的 planVersion）
     *
     * @param prevConfig 上一版完整配置
     * @param planVersion 回滚目标版本
     * @return 回滚配置
     */
    private TenantConfig buildRollbackConfig(TenantConfig prevConfig, String planVersion) {
        // 创建新的 TenantConfig（深拷贝 prevConfig）
        TenantConfig rollbackConfig = new TenantConfig();

        // 复制所有字段from prevConfig (使用 getValue() 提取原始类型)
        rollbackConfig.setPlanId(Long.parseLong(prevConfig.getPlanId().getValue()));  // PlanId.getValue() 返回 String
        rollbackConfig.setTenantId(prevConfig.getTenantId().getValue());  // TenantId.getValue() 返回 String
        rollbackConfig.setDeployUnit(prevConfig.getDeployUnit());
        rollbackConfig.setRouteRules(prevConfig.getRouteRules());  // ← 关键：routeRules 来自 prevConfig
        rollbackConfig.setNacosNameSpace(prevConfig.getNacosNameSpace());
        rollbackConfig.setHealthCheckEndpoints(prevConfig.getHealthCheckEndpoints());
        rollbackConfig.setDefaultFlag(prevConfig.getDefaultFlag());
        rollbackConfig.setServiceNames(prevConfig.getServiceNames());
        rollbackConfig.setMediaRoutingConfig(prevConfig.getMediaRoutingConfig());

        // ✅ 关键：设置新的 planVersion（回滚目标版本）
        rollbackConfig.setPlanVersion(Long.parseLong(planVersion));

        logger.debug("[TaskDomainService] 构造回滚配置: prevVersion={}, rollbackVersion={}, deployUnit={}, routeRules={}",
            prevConfig.getPlanVersion(), planVersion,
            prevConfig.getDeployUnit().name(),
            prevConfig.getRouteRules() != null ? prevConfig.getRouteRules().size() : 0);

        return rollbackConfig;
    }

    /**
     * T-028: 构造回滚 RuntimeContext（装填旧配置数据）
     *
     * @param task Task 聚合
     * @param rollbackConfig 回滚配置
     * @return 回滚 RuntimeContext
     */
    private TaskRuntimeContext buildRollbackContext(TaskAggregate task, TenantConfig rollbackConfig) {
        TaskRuntimeContext ctx = new TaskRuntimeContext(
            task.getPlanId(),
            task.getTaskId(),
            task.getTenantId()
        );

        // 装填旧配置的部署单元信息
        if (rollbackConfig.getDeployUnit() != null) {
            ctx.addVariable("deployUnitVersion", rollbackConfig.getDeployUnit().version());
            ctx.addVariable("deployUnitId", rollbackConfig.getDeployUnit().id());
            ctx.addVariable("deployUnitName", rollbackConfig.getDeployUnit().name());
        }

        // 装填健康检查端点
        if (rollbackConfig.getHealthCheckEndpoints() != null) {
            ctx.addVariable("healthCheckEndpoints", rollbackConfig.getHealthCheckEndpoints());
        }

        // ✅ 关键：设置新的 planVersion（RedisAckService.verify 会用到）
        ctx.addVariable("planVersion", rollbackConfig.getPlanVersion());

        logger.debug("[TaskDomainService] 构造回滚 RuntimeContext: deployUnit={}, version={}, planVersion={}",
            rollbackConfig.getDeployUnit() != null ? rollbackConfig.getDeployUnit().name() : null,
            rollbackConfig.getDeployUnit() != null ? rollbackConfig.getDeployUnit().version() : null,
            rollbackConfig.getPlanVersion());

        return ctx;
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

