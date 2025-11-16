package xyz.firestige.executor.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.factory.PlanFactory;
import xyz.firestige.executor.orchestration.PlanOrchestrator;
import xyz.firestige.executor.orchestration.TaskScheduler;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.support.conflict.ConflictRegistry;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.execution.TaskExecutor;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.domain.plan.PlanAggregate;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.validation.ValidationSummary;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.service.health.HealthCheckClient;
import xyz.firestige.executor.service.health.MockHealthCheckClient;
import xyz.firestige.executor.domain.state.PlanStateMachine;
import xyz.firestige.executor.domain.plan.PlanStatus;
import xyz.firestige.executor.domain.plan.PlanContext;
import xyz.firestige.executor.domain.stage.StageFactory;
import xyz.firestige.executor.domain.stage.DefaultStageFactory;
import xyz.firestige.executor.execution.TaskWorkerFactory;
import xyz.firestige.executor.execution.DefaultTaskWorkerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部署任务 Facade 实现
 */
public class DeploymentTaskFacadeImpl implements DeploymentTaskFacade {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentTaskFacadeImpl.class);

    // 核心依赖
    private final ValidationChain validationChain;
    private final TaskStateManager stateManager;

    // 运行配置与外部客户端
    private final ExecutorProperties executorProperties; // injected
    private final HealthCheckClient healthCheckClient;   // injected or default

    // 其他组件
    private final TaskScheduler taskScheduler = new TaskScheduler(Runtime.getRuntime().availableProcessors());
    private final ConflictRegistry conflictRegistry = new ConflictRegistry();
    private final PlanFactory planFactory = new PlanFactory();
    private final CheckpointService checkpointService = new CheckpointService(new InMemoryCheckpointStore());
    private final PlanOrchestrator planOrchestrator; // init in ctor
    private final SpringTaskEventSink springSink; // init in ctor

    // 内部注册表
    private final Map<String, PlanAggregate> planRegistry = new HashMap<>();
    private final Map<String, TaskAggregate> taskRegistry = new HashMap<>();
    private final Map<String, TaskRuntimeContext> contextRegistry = new HashMap<>();
    private final Map<String, List<TaskStage>> stageRegistry = new HashMap<>();
    private final Map<String, TaskExecutor> executorRegistry = new HashMap<>();
    private final Map<String, PlanStateMachine> planSmRegistry = new HashMap<>();

    private final StageFactory stageFactory = new DefaultStageFactory();
    private final TaskWorkerFactory workerFactory = new DefaultTaskWorkerFactory();

    @Override
    public TaskCreationResult createSwitchTask(List<TenantDeployConfig> configs) {
        logger.info("[Phase7] 开始创建切换任务（新架构），配置数量: {}", configs != null ? configs.size() : 0);

        try {
            // Step 1: 校验配置
            if (configs == null || configs.isEmpty()) {
                FailureInfo failureInfo = FailureInfo.of(ErrorType.VALIDATION_ERROR, "配置列表不能为空");
                return TaskCreationResult.failure(failureInfo, "配置列表为空");
            }

            Long planIdInput = configs.get(0).getPlanId();
            String planId = planIdInput != null ? String.valueOf(planIdInput) : generateTaskId();

            // 校验链仍沿用旧逻辑（不改接口）
            ValidationSummary validationSummary = validationChain.validateAll(configs);
            if (validationSummary.hasErrors()) {
                FailureInfo failureInfo = FailureInfo.of(ErrorType.VALIDATION_ERROR, "配置校验失败，有 " + validationSummary.getInvalidCount() + " 个无效配置");
                stateManager.publishTaskValidationFailedEvent(planId, failureInfo, validationSummary.getAllErrors());
                stateManager.initializeTask(planId, TaskStatus.VALIDATION_FAILED);
                TaskCreationResult result = TaskCreationResult.validationFailure(validationSummary);
                result.setPlanId(planId);
                return result;
            }

            // 构建 Plan 聚合与内部 Task 模型
            PlanAggregate plan = planFactory.createPlan(planId, configs);
            // 初始化 Plan 状态机（SM-06）
            PlanStateMachine psm = new PlanStateMachine(PlanStatus.READY);
            planSmRegistry.put(planId, psm);
            plan.setStatus(PlanStatus.READY);
            for (int i=0;i<configs.size();i++) {
                TenantDeployConfig cfg = configs.get(i);
                TaskAggregate t = plan.getTasks().get(i);
                List<TaskStage> stages = stageFactory.buildStages(t, cfg, executorProperties, healthCheckClient);
                stageRegistry.put(t.getTaskId(), stages);
            }
            stateManager.initializeTask(planId, TaskStatus.PENDING);
            stateManager.publishTaskValidatedEvent(planId, validationSummary.getValidCount());

            List<String> taskIds = new ArrayList<>();
            for (TaskAggregate t : plan.getTasks()) {
                taskIds.add(t.getTaskId());
                stateManager.initializeTask(t.getTaskId(), TaskStatus.PENDING);
                // 注册聚合 + 上下文占位（上下文稍后在执行时创建）暂时 totalStages=1（后续根据stage数量更新）
            }
            // 注册任务与上下文并启动执行
            planOrchestrator.submitPlan(plan, task -> (taskIdParam) -> {
                // 计划进入 RUNNING（状态机迁移）
                PlanStateMachine sm = planSmRegistry.get(planId);
                if (sm != null) sm.transitionTo(PlanStatus.RUNNING, new PlanContext(planId));
                plan.setStatus(PlanStatus.RUNNING);
                plan.setStartedAt(java.time.LocalDateTime.now());
                List<TaskStage> stages = stageRegistry.getOrDefault(task.getTaskId(), List.of());
                // register stage names for later cancellation event enrichment
                List<String> names = stages.stream().map(TaskStage::getName).toList();
                stateManager.registerStageNames(task.getTaskId(), names);
                TaskRuntimeContext ctx = new TaskRuntimeContext(planId, task.getTaskId(), task.getTenantId(), new PipelineContext(task.getTaskId(), null));
                contextRegistry.put(task.getTaskId(), ctx);
                stateManager.registerTaskAggregate(task.getTaskId(), task, ctx, stages.size());
                stateManager.updateState(task.getTaskId(), TaskStatus.RUNNING); // 进入运行态
                TaskExecutor executor = workerFactory.create(planId, task, stages, ctx, checkpointService, springSink, executorProperties.getTaskProgressIntervalSeconds(), stateManager, conflictRegistry);
                executorRegistry.put(task.getTaskId(), executor);
                executor.execute();
            });

            stateManager.updateState(planId, TaskStatus.RUNNING);
            stateManager.publishTaskStartedEvent(planId, plan.getTasks().size());

            planRegistry.put(planId, plan);
            plan.getTasks().forEach(t -> taskRegistry.put(t.getTaskId(), t));

            TaskCreationResult result = TaskCreationResult.success(planId, taskIds);
            result.setMessage("计划创建并提交成功 (新架构)");
            return result;
        } catch (Exception e) {
            logger.error("[Phase7] 新架构任务创建失败", e);
            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, "createSwitchTask:new");
            return TaskCreationResult.failure(failureInfo, "新架构任务创建失败: " + e.getMessage());
        }
    }

    // 新架构暂停实现（租户）
    @Override
    public TaskOperationResult pauseTaskByTenant(String tenantId) {
        TaskAggregate target = taskRegistry.values().stream().filter(t -> tenantId.equals(t.getTenantId())).findFirst().orElse(null);
        if (target == null) return TaskOperationResult.failure("未找到租户任务");
        TaskRuntimeContext ctx = contextRegistry.get(target.getTaskId());
        if (ctx != null) ctx.requestPause();
        return TaskOperationResult.success(target.getTaskId(), TaskStatus.PAUSED, "租户任务暂停请求已登记，下一 Stage 生效");
    }

    @Override
    public TaskOperationResult pauseTaskByPlan(Long planId) {
        String pid = String.valueOf(planId);
        PlanAggregate plan = planRegistry.get(pid);
        if (plan == null) return TaskOperationResult.failure("计划不存在");
        plan.getTasks().forEach(t -> {
            TaskRuntimeContext c = contextRegistry.get(t.getTaskId());
            if (c != null) c.requestPause();
        });
        PlanStateMachine sm = planSmRegistry.get(pid);
        if (sm != null) sm.transitionTo(PlanStatus.PAUSED, new PlanContext(pid));
        plan.setStatus(PlanStatus.PAUSED);
        return TaskOperationResult.success(pid, TaskStatus.PAUSED, "计划暂停请求已登记，下一 Stage 生效");
    }

    @Override
    public TaskOperationResult resumeTaskByTenant(String tenantId) {
        TaskAggregate target = taskRegistry.values().stream().filter(t -> tenantId.equals(t.getTenantId())).findFirst().orElse(null);
        if (target == null) return TaskOperationResult.failure("未找到租户任务");
        TaskRuntimeContext ctx = contextRegistry.get(target.getTaskId());
        if (ctx != null) ctx.clearPause();
        return TaskOperationResult.success(target.getTaskId(), TaskStatus.RUNNING, "租户任务恢复请求已登记");
    }

    @Override
    public TaskOperationResult resumeTaskByPlan(Long planId) {
        String pid = String.valueOf(planId);
        PlanAggregate plan = planRegistry.get(pid);
        if (plan == null) return TaskOperationResult.failure("计划不存在");
        plan.getTasks().forEach(t -> {
            TaskRuntimeContext c = contextRegistry.get(t.getTaskId());
            if (c != null) c.clearPause();
        });
        PlanStateMachine sm = planSmRegistry.get(pid);
        if (sm != null) sm.transitionTo(PlanStatus.RUNNING, new PlanContext(pid));
        plan.setStatus(PlanStatus.RUNNING);
        return TaskOperationResult.success(pid, TaskStatus.RUNNING, "计划恢复请求已登记");
    }

    @Override
    public TaskOperationResult rollbackTaskByTenant(String tenantId) {
        TaskAggregate target = taskRegistry.values().stream().filter(t -> tenantId.equals(t.getTenantId())).findFirst().orElse(null);
        if (target == null) return TaskOperationResult.failure("未找到租户任务");
        TaskExecutor exec = executorRegistry.get(target.getTaskId());
        if (exec == null) {
            List<TaskStage> stages = stageRegistry.getOrDefault(target.getTaskId(), List.of());
            TaskRuntimeContext ctx = contextRegistry.get(target.getTaskId());
            exec = workerFactory.create(target.getPlanId(), target, stages, ctx, checkpointService, springSink, executorProperties.getTaskProgressIntervalSeconds(), stateManager, conflictRegistry);
        }
        List<String> stageNames = getAndRegStageNames(target);
        springSink.publishTaskRollingBack(target.getPlanId(), target.getTaskId(), stageNames, 0);
        var res = exec.invokeRollback();
        if (target.getStatus() == TaskStatus.ROLLED_BACK) {
            springSink.publishTaskRolledBack(target.getPlanId(), target.getTaskId(), stageNames, 0);
        } else if (target.getStatus() == TaskStatus.ROLLBACK_FAILED) {
            stateManager.publishTaskRollbackFailedEvent(target.getTaskId(), FailureInfo.of(ErrorType.SYSTEM_ERROR, "rollback failed"), null);
        }
        return TaskOperationResult.success(target.getTaskId(), target.getStatus(), "租户任务回滚结束: " + res.getFinalStatus());
    }

    private List<String> getAndRegStageNames(TaskAggregate target) {
        List<String> stageNames = stageRegistry.getOrDefault(target.getTaskId(), List.of())
                .stream()
                .map(TaskStage::getName)
                .toList();
        stateManager.registerStageNames(target.getTaskId(), stageNames);
        return stageNames;
    }

    @Override
    public TaskOperationResult rollbackTaskByPlan(Long planId) {
        String pid = String.valueOf(planId);
        PlanAggregate plan = planRegistry.get(pid);
        if (plan == null) return TaskOperationResult.failure("计划不存在");
        plan.getTasks().forEach(t -> {
            List<String> stageNames = getAndRegStageNames(t);
            springSink.publishTaskRollingBack(pid, t.getTaskId(), stageNames, 0);
        });
        plan.getTasks().forEach(t -> {
            TaskExecutor exec = executorRegistry.get(t.getTaskId());
            if (exec == null) {
                List<TaskStage> stages = stageRegistry.getOrDefault(t.getTaskId(), List.of());
                TaskRuntimeContext c = contextRegistry.get(t.getTaskId());
                exec = workerFactory.create(pid, t, stages, c, checkpointService, springSink, executorProperties.getTaskProgressIntervalSeconds(), stateManager, conflictRegistry);
            }
            exec.invokeRollback();
            List<TaskStage> stages = stageRegistry.getOrDefault(t.getTaskId(), List.of());
            List<String> stageNames = new java.util.ArrayList<>();
            for (TaskStage s : stages) stageNames.add(s.getName());
            if (t.getStatus() == TaskStatus.ROLLED_BACK) {
                springSink.publishTaskRolledBack(pid, t.getTaskId(), stageNames, 0);
            } else if (t.getStatus() == TaskStatus.ROLLBACK_FAILED) {
                stateManager.publishTaskRollbackFailedEvent(t.getTaskId(), FailureInfo.of(ErrorType.SYSTEM_ERROR, "rollback failed"), null);
            }
        });
        return TaskOperationResult.success(pid, TaskStatus.ROLLED_BACK, "计划回滚完成");
    }

    @Override
    public TaskOperationResult retryTaskByTenant(String tenantId, boolean fromCheckpoint) {
        TaskAggregate target = taskRegistry.values().stream().filter(t -> tenantId.equals(t.getTenantId())).findFirst().orElse(null);
        if (target == null) return TaskOperationResult.failure("未找到租户任务");
        TaskExecutor exec = executorRegistry.get(target.getTaskId());
        if (exec == null) {
            List<TaskStage> stages = stageRegistry.getOrDefault(target.getTaskId(), List.of());
            TaskRuntimeContext ctx = contextRegistry.get(target.getTaskId());
            exec = workerFactory.create(target.getPlanId(), target, stages, ctx, checkpointService, springSink, executorProperties.getTaskProgressIntervalSeconds(), stateManager, conflictRegistry);
            executorRegistry.put(target.getTaskId(), exec);
        }
        // 补偿进度事件（checkpoint retry）
        if (fromCheckpoint) {
            int completed = target.getCurrentStageIndex();
            int total = stageRegistry.getOrDefault(target.getTaskId(), List.of()).size();
            stateManager.publishTaskProgressEvent(target.getTaskId(), null, completed, total);
        }
        var res = exec.retry(fromCheckpoint);
        return TaskOperationResult.success(target.getTaskId(), res.getFinalStatus(), "租户任务重试启动");
    }

    @Override
    public TaskOperationResult retryTaskByPlan(Long planId, boolean fromCheckpoint) {
        String pid = String.valueOf(planId);
        PlanAggregate plan = planRegistry.get(pid);
        if (plan == null) return TaskOperationResult.failure("计划不存在");
        plan.getTasks().forEach(t -> {
            TaskExecutor exec = executorRegistry.get(t.getTaskId());
            if (exec == null) {
                List<TaskStage> stages = stageRegistry.getOrDefault(t.getTaskId(), List.of());
                TaskRuntimeContext c = contextRegistry.get(t.getTaskId());
                exec = workerFactory.create(pid, t, stages, c, checkpointService, springSink, executorProperties.getTaskProgressIntervalSeconds(), stateManager, conflictRegistry);
                executorRegistry.put(t.getTaskId(), exec);
            }
            if (fromCheckpoint) {
                int completed = t.getCurrentStageIndex();
                int total = stageRegistry.getOrDefault(t.getTaskId(), List.of()).size();
                stateManager.publishTaskProgressEvent(t.getTaskId(), null, completed, total);
            }
            exec.retry(fromCheckpoint);
        });
        return TaskOperationResult.success(pid, TaskStatus.RUNNING, "计划重试启动");
    }

    @Override
    public TaskStatusInfo queryTaskStatus(String executionUnitId) {
        TaskAggregate t = taskRegistry.get(executionUnitId);
        if (t == null) return TaskStatusInfo.failure("任务不存在: " + executionUnitId);
        int completed = t.getCurrentStageIndex();
        int total = stageRegistry.getOrDefault(executionUnitId, List.of()).size();
        TaskExecutor exec = executorRegistry.get(executionUnitId);
        String currentStage = exec != null ? exec.getCurrentStageName() : null;
        TaskRuntimeContext ctx = contextRegistry.get(executionUnitId);
        boolean paused = ctx != null && ctx.isPauseRequested();
        boolean cancelled = ctx != null && ctx.isCancelRequested();
        double progress = total == 0 ? 0 : (completed * 100.0 / total);
        TaskStatusInfo info = new TaskStatusInfo(executionUnitId, t.getStatus());
        info.setMessage(String.format("进度 %.2f%% (%d/%d), currentStage=%s, paused=%s, cancelled=%s", progress, completed, total, currentStage, paused, cancelled));
        return info;
    }

    @Override
    public TaskStatusInfo queryTaskStatusByTenant(String tenantId) {
        TaskAggregate t = taskRegistry.values().stream().filter(x -> tenantId.equals(x.getTenantId())).findFirst().orElse(null);
        if (t == null) return TaskStatusInfo.failure("未找到租户任务: " + tenantId);
        return queryTaskStatus(t.getTaskId());
    }

    @Override
    public TaskOperationResult cancelTask(String executionUnitId) {
        TaskAggregate t = taskRegistry.get(executionUnitId);
        if (t == null) return TaskOperationResult.failure("任务不存在");
        TaskRuntimeContext ctx = contextRegistry.get(t.getTaskId());
        if (ctx != null) ctx.requestCancel();
        stateManager.updateState(t.getTaskId(), TaskStatus.CANCELLED);
        stateManager.publishTaskCancelledEvent(t.getTaskId(), "facade");
        return TaskOperationResult.success(t.getTaskId(), TaskStatus.CANCELLED, "任务取消请求已登记");
    }

    @Override
    public TaskOperationResult cancelTaskByTenant(String tenantId) {
        TaskAggregate t = taskRegistry.values().stream().filter(x -> tenantId.equals(x.getTenantId())).findFirst().orElse(null);
        if (t == null) return TaskOperationResult.failure("未找到租户任务");
        return cancelTask(t.getTaskId());
    }

    /**
     * 生成任务 ID
     */
    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }


    public DeploymentTaskFacadeImpl(ValidationChain validationChain, TaskStateManager stateManager) {
        this.validationChain = validationChain;
        this.stateManager = stateManager;
        this.executorProperties = new ExecutorProperties();
        this.healthCheckClient = new MockHealthCheckClient();
        this.springSink = new SpringTaskEventSink(this.stateManager, this.conflictRegistry);
        this.planOrchestrator = new PlanOrchestrator(taskScheduler, conflictRegistry, executorProperties);
    }
    public DeploymentTaskFacadeImpl() { // convenience for tests (no legacy deps)
        this.validationChain = new ValidationChain();
        this.stateManager = new TaskStateManager();
        this.executorProperties = new ExecutorProperties();
        this.healthCheckClient = new MockHealthCheckClient();
        this.springSink = new SpringTaskEventSink(this.stateManager, this.conflictRegistry);
        this.planOrchestrator = new PlanOrchestrator(taskScheduler, conflictRegistry, executorProperties);
    }
    public DeploymentTaskFacadeImpl(ValidationChain validationChain, TaskStateManager stateManager,
                                    ExecutorProperties executorProperties, HealthCheckClient healthCheckClient) {
        this.validationChain = validationChain;
        this.stateManager = stateManager;
        this.executorProperties = executorProperties != null ? executorProperties : new ExecutorProperties();
        this.healthCheckClient = healthCheckClient != null ? healthCheckClient : new MockHealthCheckClient();
        this.springSink = new SpringTaskEventSink(this.stateManager, this.conflictRegistry);
        this.planOrchestrator = new PlanOrchestrator(taskScheduler, conflictRegistry, this.executorProperties);
    }
}
