package xyz.firestige.deploy.application;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.application.plan.DeploymentPlanCreator;
import xyz.firestige.deploy.application.plan.PlanCreationContext;
import xyz.firestige.deploy.application.plan.PlanCreationException;
import xyz.firestige.deploy.config.ExecutorProperties;
import xyz.firestige.deploy.domain.plan.PlanCreationResult;
import xyz.firestige.deploy.domain.plan.PlanDomainService;
import xyz.firestige.deploy.domain.plan.PlanOperationResult;
import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.task.StateTransitionService;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.TaskOperationResult;
import xyz.firestige.deploy.domain.task.TaskRepository;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskRuntimeRepository;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.facade.TaskStatusInfo;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerCreationContext;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;
import xyz.firestige.deploy.infrastructure.state.TaskStateManager;

/**
 * 部署应用服务（RF-17: 依赖注入优化版）
 * <p>
 * 职责：
 * 1. 协调各种部署操作（创建、暂停、恢复等）
 * 2. 委托具体逻辑给专门的组件（DeploymentPlanCreator、DomainService）
 * 3. 统一返回 Result DTOs
 * 4. 异常处理和日志记录
 * 5. 事务边界管理（RF-12）
 * 6. 租户冲突检测（RF-14：合并策略）
 * 7. 执行器创建协调（RF-17：通过工厂封装）
 * <p>
 * 设计说明：
 * - RF-10 重构：提取 DeploymentPlanCreator，简化职责
 * - RF-12 重构：添加 @Transactional，集成调度策略
 * - RF-14 重构：合并 ConflictRegistry + PlanSchedulingStrategy
 * - RF-15 重构：从领域层接管 TaskExecutor 创建和执行职责
 * - RF-16 重构：引入 TaskExecutorFactory，依赖从 9 个减少到 6 个
 * - RF-17 重构：基础设施依赖注入到工厂，依赖从 6 个减少到 5 个
 * - 应用服务只做协调，不包含具体业务逻辑
 * - 单一职责：部署操作的统一入口
 *
 * @since RF-17: 基础设施依赖注入到 TaskWorkerFactory
 */
public class DeploymentApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentApplicationService.class);

    private final DeploymentPlanCreator deploymentPlanCreator;
    private final PlanDomainService planDomainService;
    private final TaskDomainService taskDomainService;
    private final TenantConflictManager conflictManager;
    private final TaskWorkerFactory taskWorkerFactory;
    private final TaskRepository taskRepository;
    private final TaskRuntimeRepository taskRuntimeRepository;
    private final ExecutorService executorService;
    private final Semaphore concurrencyLimit;
    private final int maxConcurrency;

    public DeploymentApplicationService(
            DeploymentPlanCreator deploymentPlanCreator,
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            TenantConflictManager conflictManager,
            TaskWorkerFactory taskWorkerFactory,
            TaskRepository taskRepository,
            TaskRuntimeRepository taskRuntimeRepository,
            ExecutorProperties executorProperties) {
        this.deploymentPlanCreator = deploymentPlanCreator;
        this.planDomainService = planDomainService;
        this.taskDomainService = taskDomainService;
        this.conflictManager = conflictManager;
        this.taskWorkerFactory = taskWorkerFactory;
        this.taskRepository = taskRepository;
        this.taskRuntimeRepository = taskRuntimeRepository;
        this.maxConcurrency = executorProperties.getMaxConcurrency();
        // 线程池大小设置为 maxConcurrency 的 2 倍，避免线程饥饿
        this.executorService = Executors.newFixedThreadPool(maxConcurrency * 2);
        // 并发许可数等于 maxConcurrency
        this.concurrencyLimit = new Semaphore(maxConcurrency);
        
        logger.info("[DeploymentApplicationService] 初始化完成，maxConcurrency: {}, 线程池大小: {}", 
            maxConcurrency, maxConcurrency * 2);
    }

    /**
     * 创建部署计划（RF-12：添加事务管理 + 调度策略检查）
     *
     * @param configs 租户配置列表（内部 DTO）
     * @return Plan 创建结果
     */
    @Transactional  // RF-12: 事务边界
    public PlanCreationResult createDeploymentPlan(@NotNull List<TenantConfig> configs) {
        logger.info("[DeploymentApplicationService] 创建部署计划，租户数量: {}",
                configs != null ? configs.size() : 0);

        try {
            // RF-14: 提取租户 ID
            List<String> tenantIds = configs.stream()
                .map(TenantConfig::getTenantId)
                .collect(Collectors.toList());

            // RF-14: 冲突检测（统一接口，纯内存操作，< 1ms）
            TenantConflictManager.ConflictCheckResult conflictCheck = conflictManager.canCreatePlan(tenantIds);
            if (!conflictCheck.isAllowed()) {
                logger.warn("[DeploymentApplicationService] 创建 Plan 被拒绝，冲突租户: {}", 
                    conflictCheck.getConflictingTenants());
                return PlanCreationResult.failure(
                    FailureInfo.of(ErrorType.CONFLICT, conflictCheck.getMessage()),
                    "请等待相关 Plan 完成，或移除冲突租户后重试"
                );
            }

            // 委托给 DeploymentPlanCreator 处理创建流程
            PlanCreationContext context = deploymentPlanCreator.createPlan(configs);

            // 检查验证结果
            if (context.hasValidationErrors()) {
                return PlanCreationResult.validationFailure(context.getValidationSummary());
            }

            // RF-14: 无需通知（TenantConflictManager 无状态，锁由 Task 注册时管理）

            // 返回成功结果（事务自动提交）
            return PlanCreationResult.success(context.getPlanInfo());

        } catch (PlanCreationException e) {
            logger.error("[DeploymentApplicationService] 创建部署计划失败", e);
            return PlanCreationResult.failure(
                    FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
                    "创建失败: " + e.getMessage()
            );
        } catch (Exception e) {
            logger.error("[DeploymentApplicationService] 创建部署计划发生未知错误", e);
            return PlanCreationResult.failure(
                    FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
                    "系统错误: " + e.getMessage()
            );
        }
    }

    // ========== Plan 级别操作（委托给 PlanDomainService）==========

    /**
     * 通用的任务执行编排模板（RF-19：代码复用优化）
     * <p>
     * 职责：
     * 1. 查询 Plan 下的所有 Task
     * 2. 检查租户冲突（通过 TenantConflictManager）
     * 3. 注册租户锁
     * 4. 创建 TaskExecutor 并异步提交
     * 5. 通过 Semaphore 控制并发数
     * <p>
     * 设计模式：模板方法 + 策略模式
     * - 编排逻辑固定，执行策略通过 Lambda 传入
     * - 复用于 execute/resume/retry/rollback 等场景
     *
     * @param planId Plan ID
     * @param executorAction 执行器要调用的方法（策略）
     * @param actionName 操作名称（用于日志）
     */
    private void orchestratePlanExecution(
            String planId,
            BiConsumer<TaskExecutor, TaskAggregate> executorAction,
            String actionName) {

        logger.info("[DeploymentApplicationService] 开始 {} Plan: {}", actionName, planId);

        try {
            // 1. 查询该 Plan 下的所有 Task
            List<TaskAggregate> tasks = taskRepository.findByPlanId(planId);

            if (tasks.isEmpty()) {
                logger.warn("[DeploymentApplicationService] Plan {} 没有关联的 Task", planId);
                return;
            }

            // 2. 为每个 Task 提交异步执行
            for (TaskAggregate task : tasks) {
                submitTaskAction(planId, task, executorAction, actionName);
            }

            logger.info("[DeploymentApplicationService] Plan {} 的所有 Task 已提交 {}，共 {} 个，maxConcurrency: {}",
                planId, actionName, tasks.size(), maxConcurrency);

        } catch (Exception e) {
            logger.error("[DeploymentApplicationService] {} Plan 失败: {}", actionName, planId, e);
        }
    }

    /**
     * 执行部署计划（由 PlanStartedListener 委托调用）
     *
     * @param planId Plan ID
     */
    public void executePlan(String planId) {
        orchestratePlanExecution(planId,
            (executor, task) -> executor.execute(),
            "执行");
    }

    /**
     * 恢复部署计划的内部实现（由 PlanResumedListener 委托调用）
     * <p>
     * 注意：由于 TaskExecutor 没有独立的 resume() 方法，
     * 恢复操作实际上是调用 retry(fromCheckpoint=true)
     *
     * @param planId Plan ID
     */
    public void resumePlanExecution(String planId) {
        orchestratePlanExecution(planId,
            (executor, task) -> executor.retry(true),
            "恢复");
    }

    /**
     * 重试部署计划的内部实现
     *
     * @param planId Plan ID
     * @param fromCheckpoint 是否从 checkpoint 恢复
     */
    public void retryPlanExecution(String planId, boolean fromCheckpoint) {
        orchestratePlanExecution(planId,
            (executor, task) -> executor.retry(fromCheckpoint),
            "重试");
    }

    /**
     * 回滚部署计划的内部实现
     *
     * @param planId Plan ID
     */
    public void rollbackPlanExecution(String planId) {
        orchestratePlanExecution(planId,
            (executor, task) -> executor.invokeRollback(),
            "回滚");
    }

    /**
     * 提交单个 Task 执行的通用模板（RF-19：代码复用优化）
     * <p>
     * 执行步骤：
     * 1. 检查租户冲突（通过 TenantConflictManager）
     * 2. 注册租户锁
     * 3. 创建执行上下文和 TaskExecutor
     * 4. 提交到线程池异步执行
     * 5. 执行时获取并发许可（Semaphore），执行完成后释放
     * <p>
     * 设计模式：模板方法 + 策略模式
     * - 具体的执行器方法通过 executorAction 参数传入（策略）
     *
     * @param planId Plan ID
     * @param task Task 聚合
     * @param executorAction 执行器要调用的方法（策略）
     * @param actionName 操作名称（用于日志）
     */
    private void submitTaskAction(
            String planId,
            TaskAggregate task,
            BiConsumer<TaskExecutor, TaskAggregate> executorAction,
            String actionName) {

        String taskId = task.getTaskId();
        String tenantId = task.getTenantId();

        // 1. 检查租户冲突
        if (!conflictManager.registerTask(tenantId, taskId)) {
            String conflictingTaskId = conflictManager.getConflictingTaskId(tenantId);
            logger.warn("[DeploymentApplicationService] 租户冲突，跳过 {}: taskId={}, tenantId={}, conflictingTask={}",
                actionName, taskId, tenantId, conflictingTaskId);
            return;
        }

        logger.info("[DeploymentApplicationService] Task {} 已注册租户锁，准备提交 {}", taskId, actionName);

        // 2. 异步提交执行
        executorService.submit(() -> {
            try {
                // 2.1 获取并发许可（阻塞直到有可用许可）
                logger.debug("[DeploymentApplicationService] Task {} 等待并发许可...", taskId);
                concurrencyLimit.acquire();
                logger.info("[DeploymentApplicationService] Task {} 获得并发许可，开始 {}", taskId, actionName);

                // 2.2 准备执行上下文
                TaskWorkerCreationContext context = createExecutionContext(planId, task);

                // 2.3 创建 TaskExecutor
                TaskExecutor executor = taskWorkerFactory.create(context);

                // 2.4 执行传入的策略（execute/resume/retry/rollback）
                executorAction.accept(executor, task);

                logger.info("[DeploymentApplicationService] Task {} {} 完成", taskId, actionName);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("[DeploymentApplicationService] Task {} {} 被中断", taskId, actionName, e);
            } catch (Exception e) {
                logger.error("[DeploymentApplicationService] Task {} {} 失败", taskId, actionName, e);
            } finally {
                // 2.5 释放资源
                concurrencyLimit.release();
                conflictManager.releaseTask(tenantId);
                logger.debug("[DeploymentApplicationService] Task {} 释放并发许可和租户锁", taskId);
            }
        });

        logger.info("[DeploymentApplicationService] Task {} 已提交到线程池 - {}", taskId, actionName);
    }

    /**
     * 创建任务执行上下文
     *
     * @param planId Plan ID
     * @param task Task 聚合
     * @return 任务执行上下文
     */
    private TaskWorkerCreationContext createExecutionContext(String planId, TaskAggregate task) {
        String taskId = task.getTaskId();
        
        // 从运行时仓储获取 Stages
        List<TaskStage> stages = taskRuntimeRepository.getStages(taskId)
            .orElseThrow(() -> new IllegalStateException(
                "Task stages not found: " + taskId + ", 请确保已调用 attacheStages()"));
            
        // 从运行时仓储获取或创建 RuntimeContext
        TaskRuntimeContext runtimeContext = taskRuntimeRepository.getContext(taskId)
            .orElseGet(() -> {
                TaskRuntimeContext newContext = new TaskRuntimeContext(
                    planId, 
                    taskId, 
                    task.getTenantId()
                );
                taskRuntimeRepository.saveContext(taskId, newContext);
                return newContext;
            });
            
        return TaskWorkerCreationContext.builder()
            .planId(planId)
            .task(task)
            .stages(stages)
            .runtimeContext(runtimeContext)
            .build();
    }

    /**
     * 暂停部署计划（RF-12: 添加事务管理）
     *
     * @param planId 计划 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public PlanOperationResult pausePlan(Long planId) {
        logger.info("[DeploymentApplicationService] 暂停计划: {}", planId);

        String planIdStr = String.valueOf(planId);
        try {
            planDomainService.pausePlanExecution(planIdStr);
            return PlanOperationResult.success(planIdStr, PlanStatus.PAUSED, "计划已暂停");
        } catch (Exception e) {
            logger.error("[DeploymentApplicationService] 暂停计划失败: {}", planId, e);
            return PlanOperationResult.failure(
                planIdStr,
                FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
                "暂停失败"
            );
        }
    }

    /**
     * 恢复部署计划（RF-12: 添加事务管理）
     *
     * @param planId 计划 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public PlanOperationResult resumePlan(Long planId) {
        logger.info("[DeploymentApplicationService] 恢复计划: {}", planId);

        String planIdStr = String.valueOf(planId);
        try {
            planDomainService.resumePlanExecution(planIdStr);
            return PlanOperationResult.success(planIdStr, PlanStatus.RUNNING, "计划已恢复");
        } catch (Exception e) {
            logger.error("[DeploymentApplicationService] 恢复计划失败: {}", planId, e);
            return PlanOperationResult.failure(
                planIdStr,
                FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage()),
                "恢复失败"
            );
        }
    }

    // ========== Task 级别操作（委托给 TaskDomainService）==========

    /**
     * 根据租户 ID 暂停任务（RF-12: 添加事务管理）
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult pauseTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 暂停租户任务: {}", tenantId);
        return taskDomainService.pauseTaskByTenant(tenantId);
    }

    /**
     * 根据租户 ID 恢复任务（RF-12: 添加事务管理）
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult resumeTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 恢复租户任务: {}", tenantId);
        return taskDomainService.resumeTaskByTenant(tenantId);
    }

    /**
     * 根据租户 ID 回滚任务（RF-17: 应用层创建执行器）
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult rollbackTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 回滚租户任务: {}", tenantId);

        // Step 1: 调用领域服务准备回滚
        TaskWorkerCreationContext context = taskDomainService.prepareRollbackByTenant(tenantId);
        if (context == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // Step 2: 创建或复用 TaskExecutor
        TaskExecutor executor = context.hasExistingExecutor()
            ? context.getExistingExecutor()
            : createTaskExecutor(context);

        // Step 3: 执行回滚
        var result = executor.invokeRollback();

        // Step 4: 发布回滚结果事件
        TaskStatus finalStatus = context.getTask().getStatus();
        if (finalStatus == TaskStatus.ROLLED_BACK) {
            // 聚合产生的事件已由 TaskDomainService 发布
        } else if (finalStatus == TaskStatus.ROLLBACK_FAILED) {

        }

        logger.info("[DeploymentApplicationService] 租户任务回滚结束: {}, status: {}",
                    tenantId, result.getFinalStatus());
        return TaskOperationResult.success(
            context.getTask().getTaskId(),
            finalStatus,
            "租户任务回滚结束: " + result.getFinalStatus()
        );
    }

    /**
     * 根据租户 ID 重试任务（RF-17: 应用层创建执行器）
     *
     * @param tenantId 租户 ID
     * @param fromCheckpoint 是否从 checkpoint 恢复
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult retryTaskByTenant(String tenantId, boolean fromCheckpoint) {
        logger.info("[DeploymentApplicationService] 重试租户任务: {}, fromCheckpoint: {}",
                    tenantId, fromCheckpoint);

        // Step 1: 调用领域服务准备重试
        TaskWorkerCreationContext context = taskDomainService.prepareRetryByTenant(tenantId, fromCheckpoint);
        if (context == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // Step 2: 创建或复用 TaskExecutor
        TaskExecutor executor = context.hasExistingExecutor()
            ? context.getExistingExecutor()
            : createTaskExecutor(context);

        // Step 3: 执行重试
        var result = executor.retry(fromCheckpoint);

        logger.info("[DeploymentApplicationService] 租户任务重试启动: {}, status: {}",
                    tenantId, result.getFinalStatus());
        return TaskOperationResult.success(
            context.getTask().getTaskId(),
            result.getFinalStatus(),
            "租户任务重试启动"
        );
    }

    /**
     * 根据租户 ID 取消任务（RF-12: 添加事务管理）
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional  // RF-12: 事务边界
    public TaskOperationResult cancelTaskByTenant(String tenantId) {
        logger.info("[DeploymentApplicationService] 取消租户任务: {}", tenantId);
        return taskDomainService.cancelTaskByTenant(tenantId);
    }

    /**
     * 查询任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态信息
     */
    public TaskStatusInfo queryTaskStatus(String taskId) {
        logger.debug("[DeploymentApplicationService] 查询任务状态: {}", taskId);
        return taskDomainService.queryTaskStatus(taskId);
    }

    /**
     * 根据租户 ID 查询任务状态
     *
     * @param tenantId 租户 ID
     * @return 任务状态信息
     */
    public TaskStatusInfo queryTaskStatusByTenant(String tenantId) {
        logger.debug("[DeploymentApplicationService] 查询租户任务状态: {}", tenantId);
        return taskDomainService.queryTaskStatusByTenant(tenantId);
    }

    // ========== 辅助方法 (RF-17) ==========

    /**
     * 创建 TaskExecutor（RF-17: 直接委托给 TaskWorkerFactory）
     *
     * @param context Task 创建上下文
     * @return TaskExecutor 实例
     */
    private TaskExecutor createTaskExecutor(TaskWorkerCreationContext context) {
        return taskWorkerFactory.create(context);
    }
}

