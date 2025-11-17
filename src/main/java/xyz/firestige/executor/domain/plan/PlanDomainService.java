package xyz.firestige.executor.domain.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.domain.task.TaskInfo;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.domain.plan.PlanAggregate;
import xyz.firestige.executor.domain.plan.PlanContext;
import xyz.firestige.executor.domain.plan.PlanStatus;
import xyz.firestige.executor.domain.stage.StageFactory;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.state.PlanStateMachine;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.TaskExecutor;
import xyz.firestige.executor.execution.TaskWorkerFactory;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.factory.PlanFactory;
import xyz.firestige.executor.orchestration.PlanOrchestrator;
import xyz.firestige.executor.service.health.HealthCheckClient;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.support.conflict.ConflictRegistry;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.validation.ValidationSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan 领域服务 (DDD 重构)
 *
 * 职责（重新定义）：
 * 1. Plan 聚合的创建和管理
 * 2. Plan 状态管理（暂停/恢复）
 * 3. Plan 生命周期操作
 * 4. 只关注 Plan 单聚合的业务逻辑
 *
 * 不再负责：
 * - Task 的创建（移到 TaskDomainService）
 * - 跨聚合协调（移到 DeploymentApplicationService）
 *
 * @since DDD 重构 Phase 2.2.1
 */
public class PlanDomainService {

    private static final Logger logger = LoggerFactory.getLogger(PlanDomainService.class);

    // 核心依赖（DDD 重构后简化）
    private final PlanRepository planRepository;
    private final TaskStateManager stateManager;
    private final PlanFactory planFactory;
    private final PlanOrchestrator planOrchestrator;
    private final ExecutorProperties executorProperties;

    // TODO: 以下依赖应该移到 TaskDomainService 或 DeploymentApplicationService
    private final StageFactory stageFactory;
    private final TaskWorkerFactory workerFactory;
    private final CheckpointService checkpointService;
    private final SpringTaskEventSink eventSink;
    private final ConflictRegistry conflictRegistry;
    private final ValidationChain validationChain;
    private final HealthCheckClient healthCheckClient;

    // TODO: 这些注册表应该通过 TaskRepository 访问
    private final Map<String, TaskAggregate> taskRegistry = new HashMap<>();
    private final Map<String, TaskRuntimeContext> contextRegistry = new HashMap<>();
    private final Map<String, List<TaskStage>> stageRegistry = new HashMap<>();
    private final Map<String, TaskExecutor> executorRegistry = new HashMap<>();

    public PlanDomainService(
            PlanRepository planRepository,
            ValidationChain validationChain,
            TaskStateManager stateManager,
            PlanFactory planFactory,
            PlanOrchestrator planOrchestrator,
            StageFactory stageFactory,
            TaskWorkerFactory workerFactory,
            ExecutorProperties executorProperties,
            HealthCheckClient healthCheckClient,
            CheckpointService checkpointService,
            SpringTaskEventSink eventSink,
            ConflictRegistry conflictRegistry) {
        this.planRepository = planRepository;
        this.validationChain = validationChain;
        this.stateManager = stateManager;
        this.planFactory = planFactory;
        this.planOrchestrator = planOrchestrator;
        this.stageFactory = stageFactory;
        this.workerFactory = workerFactory;
        this.executorProperties = executorProperties;
        this.healthCheckClient = healthCheckClient;
        this.checkpointService = checkpointService;
        this.eventSink = eventSink;
        this.conflictRegistry = conflictRegistry;
    }

    /**
     * 创建切换任务 (Plan + Tasks)
     * 注意：此方法目前仍接收外部 DTO（TenantDeployConfig），等待 Facade 层创建后再改为 TenantConfig
     * @param configs 租户配置列表
     * @return PlanCreationResult 包含成功/失败信息和 Plan 聚合信息
     */
    public PlanCreationResult createSwitchTask(List<TenantDeployConfig> configs) {
        logger.info("[PlanApplicationService] 开始创建切换任务，配置数量: {}", configs != null ? configs.size() : 0);

        try {
            // Step 1: 参数校验
            if (configs == null || configs.isEmpty()) {
                FailureInfo failureInfo = FailureInfo.of(ErrorType.VALIDATION_ERROR, "配置列表不能为空");
                return PlanCreationResult.failure(failureInfo, "配置列表为空");
            }

            Long planIdInput = configs.get(0).getPlanId();
            String planId = planIdInput != null ? String.valueOf(planIdInput) : generatePlanId();

            // Step 2: 业务校验
            ValidationSummary validationSummary = validationChain.validateAll(configs);
            if (validationSummary.hasErrors()) {
                FailureInfo failureInfo = FailureInfo.of(
                    ErrorType.VALIDATION_ERROR,
                    "配置校验失败，有 " + validationSummary.getInvalidCount() + " 个无效配置"
                );
                stateManager.publishTaskValidationFailedEvent(planId, failureInfo, validationSummary.getAllErrors());
                stateManager.initializeTask(planId, TaskStatus.VALIDATION_FAILED);
                return PlanCreationResult.validationFailure(validationSummary);
            }

            // Step 3: 构建 Plan 聚合
            PlanAggregate plan = planFactory.createPlan(planId, configs);
            // 新增：初始化最大并发数，避免 PlanInfo.from 空指针
            if (plan.getMaxConcurrency() == null) {
                plan.setMaxConcurrency(executorProperties.getMaxConcurrency());
            }

            // Step 4: 初始化 Plan 状态机
            PlanStateMachine psm = new PlanStateMachine(PlanStatus.READY);
            planRepository.saveStateMachine(planId, psm);
            plan.setStatus(PlanStatus.READY);

            // Step 5: 构建 Stage（每个 Task）
            for (int i = 0; i < configs.size(); i++) {
                TenantDeployConfig cfg = configs.get(i);
                TaskAggregate task = plan.getTasks().get(i);
                List<TaskStage> stages = stageFactory.buildStages(task, cfg, executorProperties, healthCheckClient);
                stageRegistry.put(task.getTaskId(), stages);
            }

            // Step 6: 初始化状态
            stateManager.initializeTask(planId, TaskStatus.PENDING);
            stateManager.publishTaskValidatedEvent(planId, validationSummary.getValidCount());

            // Step 7: 注册 Task
            List<String> taskIds = new ArrayList<>();
            for (TaskAggregate task : plan.getTasks()) {
                taskIds.add(task.getTaskId());
                stateManager.initializeTask(task.getTaskId(), TaskStatus.PENDING);
            }

            // Step 8: 提交 Plan 执行
            planOrchestrator.submitPlan(plan, task -> (taskIdParam) -> {
                // Plan 进入 RUNNING 状态
                PlanStateMachine sm = planRepository.getStateMachine(planId);
                if (sm != null) {
                    sm.transitionTo(PlanStatus.RUNNING, new PlanContext(planId));
                }
                plan.setStatus(PlanStatus.RUNNING);
                plan.setStartedAt(java.time.LocalDateTime.now());

                // 准备 Task 执行上下文
                List<TaskStage> stages = stageRegistry.getOrDefault(task.getTaskId(), List.of());
                List<String> stageNames = stages.stream().map(TaskStage::getName).toList();
                stateManager.registerStageNames(task.getTaskId(), stageNames);

                TaskRuntimeContext ctx = new TaskRuntimeContext(
                    planId,
                    task.getTaskId(),
                    task.getTenantId(),
                    new PipelineContext(task.getTaskId(), null)
                );
                contextRegistry.put(task.getTaskId(), ctx);
                stateManager.registerTaskAggregate(task.getTaskId(), task, ctx, stages.size());
                stateManager.updateState(task.getTaskId(), TaskStatus.RUNNING);

                // 创建并启动 Executor（RF-02: 使用 TaskWorkerCreationContext）
                TaskExecutor executor = workerFactory.create(
                    xyz.firestige.executor.execution.TaskWorkerCreationContext.builder()
                        .planId(planId)
                        .task(task)
                        .stages(stages)
                        .runtimeContext(ctx)
                        .checkpointService(checkpointService)
                        .eventSink(eventSink)
                        .progressIntervalSeconds(executorProperties.getTaskProgressIntervalSeconds())
                        .stateManager(stateManager)
                        .conflictRegistry(conflictRegistry)
                        .build()
                );
                executorRegistry.put(task.getTaskId(), executor);
                executor.execute();
            });

            // Step 9: 更新状态并注册
            stateManager.updateState(planId, TaskStatus.RUNNING);
            stateManager.publishTaskStartedEvent(planId, plan.getTasks().size());

            planRepository.save(plan);
            plan.getTasks().forEach(t -> taskRegistry.put(t.getTaskId(), t));

            // Step 10: 返回成功结果
            PlanInfo planInfo = PlanInfo.from(plan);
            logger.info("[PlanDomainService] Plan 创建成功，planId: {}, tasks: {}", planId, plan.getTasks().size());
            return PlanCreationResult.success(planInfo);

        } catch (Exception e) {
            logger.error("[PlanApplicationService] Plan 创建失败", e);
            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, "createSwitchTask");
            return PlanCreationResult.failure(failureInfo, "Plan 创建失败: " + e.getMessage());
        }
    }

    /**
     * 暂停整个 Plan
     * @param planId 计划 ID
     * @return PlanOperationResult
     */
    public PlanOperationResult pausePlan(Long planId) {
        String pid = String.valueOf(planId);
        logger.info("[PlanDomainService] 暂停计划: {}", pid);

        PlanAggregate plan = planRepository.get(pid);
        if (plan == null) {
            return PlanOperationResult.failure(pid,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "计划不存在"),
                "计划不存在");
        }

        // 暂停所有 Task
        plan.getTasks().forEach(task -> {
            TaskRuntimeContext ctx = contextRegistry.get(task.getTaskId());
            if (ctx != null) {
                ctx.requestPause();
            }
        });

        // 更新 Plan 状态
        PlanStateMachine sm = planRepository.getStateMachine(pid);
        if (sm != null) {
            sm.transitionTo(PlanStatus.PAUSED, new PlanContext(pid));
        }
        plan.setStatus(PlanStatus.PAUSED);

        logger.info("[PlanApplicationService] 计划暂停成功: {}", pid);
        return PlanOperationResult.success(pid, PlanStatus.PAUSED, "计划暂停请求已登记，下一 Stage 生效");
    }

    /**
     * 恢复整个 Plan
     * @param planId 计划 ID
     * @return PlanOperationResult
     */
    public PlanOperationResult resumePlan(Long planId) {
        String pid = String.valueOf(planId);
        logger.info("[PlanDomainService] 恢复计划: {}", pid);

        PlanAggregate plan = planRepository.get(pid);
        if (plan == null) {
            return PlanOperationResult.failure(pid,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "计划不存在"),
                "计划不存在");
        }

        // 恢复所有 Task
        plan.getTasks().forEach(task -> {
            TaskRuntimeContext ctx = contextRegistry.get(task.getTaskId());
            if (ctx != null) {
                ctx.clearPause();
            }
        });

        // 更新 Plan 状态
        PlanStateMachine sm = planRepository.getStateMachine(pid);
        if (sm != null) {
            sm.transitionTo(PlanStatus.RUNNING, new PlanContext(pid));
        }
        plan.setStatus(PlanStatus.RUNNING);

        logger.info("[PlanApplicationService] 计划恢复成功: {}", pid);
        return PlanOperationResult.success(pid, PlanStatus.RUNNING, "计划恢复请求已登记");
    }

    /**
     * 回滚整个 Plan
     * @param planId 计划 ID
     * @return PlanOperationResult
     */
    public PlanOperationResult rollbackPlan(Long planId) {
        String pid = String.valueOf(planId);
        logger.info("[PlanDomainService] 回滚计划: {}", pid);

        PlanAggregate plan = planRepository.get(pid);
        if (plan == null) {
            return PlanOperationResult.failure(pid,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "计划不存在"),
                "计划不存在");
        }

        // 发布回滚开始事件
        plan.getTasks().forEach(task -> {
            List<String> stageNames = getAndRegStageNames(task);
            eventSink.publishTaskRollingBack(pid, task.getTaskId(), stageNames, 0);
        });

        // 执行回滚
        plan.getTasks().forEach(task -> {
            TaskExecutor exec = executorRegistry.get(task.getTaskId());
            if (exec == null) {
                List<TaskStage> stages = stageRegistry.getOrDefault(task.getTaskId(), List.of());
                TaskRuntimeContext ctx = contextRegistry.get(task.getTaskId());
                // RF-02: 使用 TaskWorkerCreationContext
                exec = workerFactory.create(
                    xyz.firestige.executor.execution.TaskWorkerCreationContext.builder()
                        .planId(pid)
                        .task(task)
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
            exec.invokeRollback();

            // 发布回滚结果事件
            List<TaskStage> stages = stageRegistry.getOrDefault(task.getTaskId(), List.of());
            List<String> stageNames = stages.stream().map(TaskStage::getName).toList();
            if (task.getStatus() == TaskStatus.ROLLED_BACK) {
                eventSink.publishTaskRolledBack(pid, task.getTaskId(), stageNames, 0);
            } else if (task.getStatus() == TaskStatus.ROLLBACK_FAILED) {
                stateManager.publishTaskRollbackFailedEvent(
                    task.getTaskId(),
                    FailureInfo.of(ErrorType.SYSTEM_ERROR, "rollback failed"),
                    null
                );
            }
        });

        logger.info("[PlanApplicationService] 计划回滚完成: {}", pid);
        return PlanOperationResult.success(pid, PlanStatus.ROLLED_BACK, "计划回滚完成");
    }

    /**
     * 重试整个 Plan
     * @param planId 计划 ID
     * @param fromCheckpoint 是否从检查点恢复
     * @return PlanOperationResult
     */
    public PlanOperationResult retryPlan(Long planId, boolean fromCheckpoint) {
        String pid = String.valueOf(planId);
        logger.info("[PlanDomainService] 重试计划: {}, fromCheckpoint: {}", pid, fromCheckpoint);

        PlanAggregate plan = planRepository.get(pid);
        if (plan == null) {
            return PlanOperationResult.failure(pid,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "计划不存在"),
                "计划不存在");
        }

        // 重试所有 Task
        plan.getTasks().forEach(task -> {
            TaskExecutor exec = executorRegistry.get(task.getTaskId());
            if (exec == null) {
                List<TaskStage> stages = stageRegistry.getOrDefault(task.getTaskId(), List.of());
                TaskRuntimeContext ctx = contextRegistry.get(task.getTaskId());
                // RF-02: 使用 TaskWorkerCreationContext
                exec = workerFactory.create(
                    xyz.firestige.executor.execution.TaskWorkerCreationContext.builder()
                        .planId(pid)
                        .task(task)
                        .stages(stages)
                        .runtimeContext(ctx)
                        .checkpointService(checkpointService)
                        .eventSink(eventSink)
                        .progressIntervalSeconds(executorProperties.getTaskProgressIntervalSeconds())
                        .stateManager(stateManager)
                        .conflictRegistry(conflictRegistry)
                        .build()
                );
                executorRegistry.put(task.getTaskId(), exec);
            }

            // 补偿进度事件（checkpoint retry）
            if (fromCheckpoint) {
                int completed = task.getCurrentStageIndex();
                int total = stageRegistry.getOrDefault(task.getTaskId(), List.of()).size();
                stateManager.publishTaskProgressEvent(task.getTaskId(), null, completed, total);
            }

            exec.retry(fromCheckpoint);
        });

        logger.info("[PlanApplicationService] 计划重试启动: {}", pid);
        return PlanOperationResult.success(pid, PlanStatus.RUNNING, "计划重试启动");
    }

    // ========== 辅助方法 ==========

    /**
     * 生成 Plan ID
     */
    private String generatePlanId() {
        return "plan_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
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

    // ========== 访问器（供 TaskApplicationService 使用）==========

    public Map<String, TaskAggregate> getTaskRegistry() {
        return taskRegistry;
    }

    public Map<String, TaskRuntimeContext> getContextRegistry() {
        return contextRegistry;
    }

    public Map<String, List<TaskStage>> getStageRegistry() {
        return stageRegistry;
    }

    public Map<String, TaskExecutor> getExecutorRegistry() {
        return executorRegistry;
    }
}
