package xyz.firestige.deploy.application.facade;

import java.util.List;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xyz.firestige.deploy.application.conflict.TenantConflictCoordinator;
import xyz.firestige.deploy.application.lifecycle.PlanLifecycleService;
import xyz.firestige.deploy.application.orchestration.TaskExecutionOrchestrator;
import xyz.firestige.deploy.application.task.TaskOperationService;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;

/**
 * 计划执行门面（RF-20: 为 Listener 专用）
 * <p>
 * 职责：
 * 1. 为事件监听器提供统一的 Plan 执行入口
 * 2. 组合 PlanLifecycleService、PlanExecutionOrchestrator、TenantConflictCoordinator
 * 3. 封装完整的执行流程（验证 → 冲突检查 → 编排执行）
 * <p>
 * 依赖（4个）：
 * - PlanLifecycleService：计划生命周期服务
 * - PlanExecutionOrchestrator：计划执行编排器
 * - TenantConflictCoordinator：租户冲突协调器
 * - TaskOperationService：任务操作服务
 * <p>
 * 设计说明：
 * - 专为事件监听器设计，简化 Listener 的依赖注入
 * - 不暴露给外部业务 Facade（外部直接使用各个独立服务）
 * - 封装完整的执行流程，保证一致性
 *
 * @since RF-20 - 服务拆分
 */
public class PlanExecutionFacade {

    private static final Logger logger = LoggerFactory.getLogger(PlanExecutionFacade.class);

    private final PlanLifecycleService planLifecycleService;
    private final TaskExecutionOrchestrator orchestrator;
    private final TenantConflictCoordinator conflictCoordinator;
    private final TaskOperationService taskOperationService;

    public PlanExecutionFacade(
            PlanLifecycleService planLifecycleService,
            TaskExecutionOrchestrator orchestrator,
            TenantConflictCoordinator conflictCoordinator,
            TaskOperationService taskOperationService) {
        this.planLifecycleService = planLifecycleService;
        this.orchestrator = orchestrator;
        this.conflictCoordinator = conflictCoordinator;
        this.taskOperationService = taskOperationService;
        
        logger.info("[PlanExecutionFacade] 初始化完成");
    }

    /**
     * 执行部署计划（给 PlanStartedListener 使用）
     * <p>
     * 流程：验证 Plan → 查询 Tasks → 编排执行
     *
     * @param planId Plan ID
     */
    public void executePlan(PlanId planId) {
        logger.info("[PlanExecutionFacade] 执行 Plan: {}", planId);

        // 1. 验证 Plan
        planLifecycleService.getAndValidatePlan(planId);

        // 2. 查询所有 Task
        List<TaskAggregate> tasks = taskOperationService.getTasksByPlanId(planId);

        // 3. 编排执行
        orchestrator.orchestrate(
            planId,
            tasks,
            createExecuteAction(),
            "执行",
            this::checkAndRegisterConflict
        );
    }

    /**
     * 恢复部署计划（给 PlanResumedListener 使用）
     * <p>
     * 流程：验证 Plan → 查询 Tasks → 编排恢复
     *
     * @param planId Plan ID
     */
    public void resumePlanExecution(PlanId planId) {
        logger.info("[PlanExecutionFacade] 恢复 Plan: {}", planId);

        // 1. 验证 Plan
        planLifecycleService.getAndValidatePlan(planId);

        // 2. 查询所有 Task
        List<TaskAggregate> tasks = taskOperationService.getTasksByPlanId(planId);

        // 3. 编排恢复（实际调用 retry(fromCheckpoint=true)）
        orchestrator.orchestrate(
            planId,
            tasks,
            createResumeAction(),
            "恢复",
            this::checkAndRegisterConflict
        );
    }

    /**
     * 重试部署计划
     *
     * @param planId Plan ID
     * @param fromCheckpoint 是否从 checkpoint 恢复
     */
    public void retryPlanExecution(PlanId planId, boolean fromCheckpoint) {
        logger.info("[PlanExecutionFacade] 重试 Plan: {}, fromCheckpoint: {}", planId, fromCheckpoint);

        // 1. 验证 Plan
        planLifecycleService.getAndValidatePlan(planId);

        // 2. 查询所有 Task
        List<TaskAggregate> tasks = taskOperationService.getTasksByPlanId(planId);

        // 3. 编排重试
        orchestrator.orchestrate(
            planId,
            tasks,
            createRetryAction(fromCheckpoint),
            "重试",
            this::checkAndRegisterConflict
        );
    }

    /**
     * 回滚部署计划
     *
     * @param planId Plan ID
     */
    public void rollbackPlanExecution(PlanId planId) {
        logger.info("[PlanExecutionFacade] 回滚 Plan: {}", planId);

        // 1. 验证 Plan
        planLifecycleService.getAndValidatePlan(planId);

        // 2. 查询所有 Task
        List<TaskAggregate> tasks = taskOperationService.getTasksByPlanId(planId);

        // 3. 编排回滚
        orchestrator.orchestrate(
            planId,
            tasks,
            createRollbackAction(),
            "回滚",
            this::checkAndRegisterConflict
        );
    }

    // ========== 私有辅助方法 ==========

    /**
     * 创建执行动作（策略模式）
     */
    private BiConsumer<TaskExecutor, TaskAggregate> createExecuteAction() {
        return (executor, task) -> executor.execute();
    }

    /**
     * 创建恢复动作（策略模式）
     */
    private BiConsumer<TaskExecutor, TaskAggregate> createResumeAction() {
        return (executor, task) -> executor.retry(true);
    }

    /**
     * 创建重试动作（策略模式）
     */
    private BiConsumer<TaskExecutor, TaskAggregate> createRetryAction(boolean fromCheckpoint) {
        return (executor, task) -> executor.retry(fromCheckpoint);
    }

    /**
     * 创建回滚动作（策略模式）
     */
    private BiConsumer<TaskExecutor, TaskAggregate> createRollbackAction() {
        return (executor, task) -> executor.invokeRollback();
    }

    /**
     * 检查并注册冲突（冲突回调）
     * <p>
     * 返回 true 表示无冲突，允许执行
     * 返回 false 表示有冲突，跳过执行
     */
    private Boolean checkAndRegisterConflict(TaskAggregate task) {
        boolean success = conflictCoordinator.checkAndRegisterTask(task);
        
        // 注册失败时需要释放（orchestrator 的 finally 会处理）
        if (!success) {
            logger.warn("[PlanExecutionFacade] Task {} 租户冲突: {}", 
                task.getTaskId(), task.getTenantId());
        }
        
        return success;
    }
}
