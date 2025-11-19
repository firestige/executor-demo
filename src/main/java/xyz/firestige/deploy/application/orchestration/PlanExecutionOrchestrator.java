package xyz.firestige.deploy.application.orchestration;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xyz.firestige.deploy.config.ExecutorProperties;
import xyz.firestige.deploy.domain.plan.PlanDomainService;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskRuntimeRepository;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerCreationContext;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

/**
 * 计划执行编排器（RF-20: DeploymentApplicationService 拆分）
 * <p>
 * 职责：
 * 1. 编排 Plan 执行流程（execute/resume/retry/rollback）
 * 2. 创建 TaskExecutor 并提交异步执行
 * 3. 管理线程池和并发控制（ExecutorService + Semaphore）
 * <p>
 * 依赖（3个）：
 * - PlanDomainService：计划领域服务
 * - TaskWorkerFactory：任务执行器工厂
 * - ExecutorProperties：执行器配置
 * <p>
 * 设计模式：模板方法 + 策略模式
 * - orchestrate() 固定编排流程
 * - 具体执行策略通过 BiConsumer 传入
 * <p>
 * 设计说明：
 * - 聚焦于任务执行的编排和调度
 * - 不涉及业务逻辑和事务管理
 * - 内部管理线程池和并发许可
 *
 * @since RF-20 - 服务拆分
 */
public class PlanExecutionOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(PlanExecutionOrchestrator.class);

    private final PlanDomainService planDomainService;
    private final TaskWorkerFactory taskWorkerFactory;
    private final TaskRuntimeRepository taskRuntimeRepository;
    private final ExecutorService executorService;
    private final Semaphore concurrencyLimit;
    private final int maxConcurrency;

    public PlanExecutionOrchestrator(
            PlanDomainService planDomainService,
            TaskWorkerFactory taskWorkerFactory,
            TaskRuntimeRepository taskRuntimeRepository,
            ExecutorProperties executorProperties) {
        this.planDomainService = planDomainService;
        this.taskWorkerFactory = taskWorkerFactory;
        this.taskRuntimeRepository = taskRuntimeRepository;
        this.maxConcurrency = executorProperties.getMaxConcurrency();
        // 线程池大小设置为 maxConcurrency 的 2 倍，避免线程饥饿
        this.executorService = Executors.newFixedThreadPool(maxConcurrency * 2);
        // 并发许可数等于 maxConcurrency
        this.concurrencyLimit = new Semaphore(maxConcurrency);
        
        logger.info("[PlanExecutionOrchestrator] 初始化完成，maxConcurrency: {}, 线程池大小: {}", 
            maxConcurrency, maxConcurrency * 2);
    }

    /**
     * 编排 Plan 执行流程（模板方法）
     * <p>
     * 执行步骤：
     * 1. 提交所有 Task 到线程池
     * 2. 通过 Semaphore 控制并发数
     * 3. 执行传入的策略（executorAction）
     *
     * @param planId Plan ID
     * @param tasks 任务列表
     * @param executorAction 执行策略（execute/resume/retry/rollback）
     * @param actionName 操作名称（用于日志）
     * @param conflictCallback 冲突检查回调（返回 true 表示允许执行）
     */
    public void orchestrate(
            String planId,
            List<TaskAggregate> tasks,
            BiConsumer<TaskExecutor, TaskAggregate> executorAction,
            String actionName,
            java.util.function.Function<TaskAggregate, Boolean> conflictCallback) {

        logger.info("[PlanExecutionOrchestrator] 开始编排 {} Plan: {}, 任务数量: {}", 
            actionName, planId, tasks.size());

        if (tasks.isEmpty()) {
            logger.warn("[PlanExecutionOrchestrator] Plan {} 没有关联的 Task", planId);
            return;
        }

        // 为每个 Task 提交异步执行
        for (TaskAggregate task : tasks) {
            submitTaskAction(planId, task, executorAction, actionName, conflictCallback);
        }

        logger.info("[PlanExecutionOrchestrator] Plan {} 的所有 Task 已提交 {}，共 {} 个，maxConcurrency: {}",
            planId, actionName, tasks.size(), maxConcurrency);
    }

    /**
     * 提交单个 Task 执行（内部方法）
     *
     * @param planId Plan ID
     * @param task Task 聚合
     * @param executorAction 执行策略
     * @param actionName 操作名称
     * @param conflictCallback 冲突检查回调
     */
    private void submitTaskAction(
            String planId,
            TaskAggregate task,
            BiConsumer<TaskExecutor, TaskAggregate> executorAction,
            String actionName,
            java.util.function.Function<TaskAggregate, Boolean> conflictCallback) {

        String taskId = task.getTaskId();
        String tenantId = task.getTenantId();

        // 1. 冲突检查（由外部传入）
        if (!conflictCallback.apply(task)) {
            logger.warn("[PlanExecutionOrchestrator] 租户冲突，跳过 {}: taskId={}, tenantId={}",
                actionName, taskId, tenantId);
            return;
        }

        logger.info("[PlanExecutionOrchestrator] Task {} 已通过冲突检查，准备提交 {}", taskId, actionName);

        // 2. 异步提交执行
        executorService.submit(() -> {
            try {
                // 2.1 获取并发许可（阻塞直到有可用许可）
                logger.debug("[PlanExecutionOrchestrator] Task {} 等待并发许可...", taskId);
                concurrencyLimit.acquire();
                logger.info("[PlanExecutionOrchestrator] Task {} 获得并发许可，开始 {}", taskId, actionName);

                // 2.2 准备执行上下文
                TaskWorkerCreationContext context = createExecutionContext(planId, task);

                // 2.3 创建 TaskExecutor
                TaskExecutor executor = taskWorkerFactory.create(context);

                // 2.4 执行传入的策略（execute/resume/retry/rollback）
                executorAction.accept(executor, task);

                logger.info("[PlanExecutionOrchestrator] Task {} {} 完成", taskId, actionName);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("[PlanExecutionOrchestrator] Task {} {} 被中断", taskId, actionName, e);
            } catch (Exception e) {
                logger.error("[PlanExecutionOrchestrator] Task {} {} 失败", taskId, actionName, e);
            } finally {
                // 2.5 释放并发许可（冲突锁由外部管理）
                concurrencyLimit.release();
                logger.debug("[PlanExecutionOrchestrator] Task {} 释放并发许可", taskId);
            }
        });

        logger.info("[PlanExecutionOrchestrator] Task {} 已提交到线程池 - {}", taskId, actionName);
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
}
