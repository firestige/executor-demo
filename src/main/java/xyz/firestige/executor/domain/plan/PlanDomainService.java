package xyz.firestige.executor.domain.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.domain.state.PlanStateMachine;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.factory.PlanFactory;
import xyz.firestige.executor.orchestration.PlanOrchestrator;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;

import java.time.LocalDateTime;

/**
 * Plan 领域服务（DDD 重构完成版）
 *
 * 职责：
 * 1. Plan 聚合的创建和管理
 * 2. Plan 状态管理
 * 3. Plan 生命周期操作
 * 4. 只关注 Plan 单聚合的业务逻辑
 *
 * 不再负责：
 * - Task 的创建（由 TaskDomainService 负责）
 * - 跨聚合协调（由 DeploymentApplicationService 负责）
 * - 业务校验（由应用层负责）
 *
 * @since DDD 重构 Phase 2.2 - 完成版
 */
public class PlanDomainService {

    private static final Logger logger = LoggerFactory.getLogger(PlanDomainService.class);

    // 核心依赖（简化后：6个）
    private final PlanRepository planRepository;
    private final TaskStateManager stateManager;
    private final PlanFactory planFactory;
    private final PlanOrchestrator planOrchestrator;
    private final SpringTaskEventSink eventSink;
    private final ExecutorProperties executorProperties;

    public PlanDomainService(
            PlanRepository planRepository,
            TaskStateManager stateManager,
            PlanFactory planFactory,
            PlanOrchestrator planOrchestrator,
            SpringTaskEventSink eventSink,
            ExecutorProperties executorProperties) {
        this.planRepository = planRepository;
        this.stateManager = stateManager;
        this.planFactory = planFactory;
        this.planOrchestrator = planOrchestrator;
        this.eventSink = eventSink;
        this.executorProperties = executorProperties;
    }

    /**
     * 创建 Plan 聚合（只创建 Plan，不创建 Task）
     * DDD 重构：简化为查询和持久化
     *
     * @param planId Plan ID
     * @param tenantCount 租户数量（暂未使用，保留接口兼容）
     * @return Plan 聚合
     */
    public PlanAggregate createPlan(String planId, int tenantCount) {
        logger.info("[PlanDomainService] 创建 Plan: {}, 租户数量: {}", planId, tenantCount);

        // ✅ 创建 Plan 聚合（业务逻辑在构造函数中）
        PlanAggregate plan = new PlanAggregate(planId);
        plan.setMaxConcurrency(executorProperties.getMaxConcurrency());

        // ✅ 标记为 READY（业务逻辑稍后在应用层调用）
        // 注意：此时 tasks 为空，需要先添加 Task 再 markAsReady()

        // 初始化 Plan 状态机
        PlanStateMachine stateMachine = new PlanStateMachine(PlanStatus.CREATED);
        planRepository.saveStateMachine(planId, stateMachine);

        // 初始化状态
        stateManager.initializeTask(planId, TaskStatus.PENDING);

        // 保存到仓储
        planRepository.save(plan);

        logger.info("[PlanDomainService] Plan 创建成功: {}", planId);
        return plan;
    }

    /**
     * 添加 Task 到 Plan（跨聚合关联，由应用层调用）
     * RF-07 重构：只传递 taskId，实现聚合间通过 ID 引用
     *
     * @param planId Plan ID
     * @param taskId Task ID（而非整个 TaskAggregate）
     */
    public void addTaskToPlan(String planId, String taskId) {
        logger.debug("[PlanDomainService] 添加 Task 到 Plan: {} -> {}", planId, taskId);

        PlanAggregate plan = planRepository.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Plan 不存在: " + planId);
        }

        // ✅ 调用聚合的业务方法（传递 ID）
        plan.addTask(taskId);
        planRepository.save(plan);

        logger.debug("[PlanDomainService] Task 添加成功: {} -> {}", planId, taskId);
    }

    /**
     * 标记 Plan 为 READY
     * DDD 重构：新增方法，调用聚合业务方法
     *
     * @param planId Plan ID
     */
    public void markPlanAsReady(String planId) {
        logger.info("[PlanDomainService] 标记 Plan 为 READY: {}", planId);

        PlanAggregate plan = planRepository.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Plan 不存在: " + planId);
        }

        // ✅ 调用聚合的业务方法
        plan.markAsReady();

        // 更新状态机
        PlanStateMachine sm = planRepository.getStateMachine(planId).orElse(null);
        if (sm != null) {
            sm.transitionTo(PlanStatus.READY, new PlanContext(planId));
        }

        planRepository.save(plan);
        logger.info("[PlanDomainService] Plan 已标记为 READY: {}", planId);
    }

    /**
     * 启动 Plan 执行
     * DDD 重构：调用聚合的业务方法
     *
     * @param planId Plan ID
     */
    public void startPlan(String planId) {
        logger.info("[PlanDomainService] 启动 Plan 执行: {}", planId);

        PlanAggregate plan = planRepository.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Plan 不存在: " + planId);
        }

        // ✅ 调用聚合的业务方法（不变式保护在聚合内部）
        plan.start();

        // 更新状态机
        PlanStateMachine sm = planRepository.getStateMachine(planId).orElse(null);
        if (sm != null) {
            sm.transitionTo(PlanStatus.RUNNING, new PlanContext(planId));
        }

        planRepository.save(plan);

        // 更新状态管理器并发布事件
        stateManager.updateState(planId, TaskStatus.RUNNING);
        stateManager.publishTaskStartedEvent(planId, plan.getTaskCount());

        logger.info("[PlanDomainService] Plan 已启动: {}", planId);
    }

    /**
     * 暂停 Plan 执行
     * DDD 重构：调用聚合的业务方法
     *
     * @param planId Plan ID
     */
    public void pausePlanExecution(String planId) {
        logger.info("[PlanDomainService] 暂停 Plan 执行: {}", planId);

        PlanAggregate plan = planRepository.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Plan 不存在: " + planId);
        }

        // ✅ 调用聚合的业务方法（不变式保护在聚合内部）
        plan.pause();

        // 更新状态机
        PlanStateMachine sm = planRepository.getStateMachine(planId).orElse(null);
        if (sm != null) {
            sm.transitionTo(PlanStatus.PAUSED, new PlanContext(planId));
        }

        planRepository.save(plan);

        logger.info("[PlanDomainService] Plan 已暂停: {}", planId);
    }

    /**
     * 恢复 Plan 执行
     * DDD 重构：调用聚合的业务方法
     *
     * @param planId Plan ID
     */
    public void resumePlanExecution(String planId) {
        logger.info("[PlanDomainService] 恢复 Plan 执行: {}", planId);

        PlanAggregate plan = planRepository.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Plan 不存在: " + planId);
        }

        // ✅ 调用聚合的业务方法（不变式保护在聚合内部）
        plan.resume();

        // 更新状态机
        PlanStateMachine sm = planRepository.getStateMachine(planId).orElse(null);
        if (sm != null) {
            sm.transitionTo(PlanStatus.RUNNING, new PlanContext(planId));
        }

        planRepository.save(plan);

        logger.info("[PlanDomainService] Plan 已恢复: {}", planId);
    }

    /**
     * 获取 Plan 信息
     *
     * @param planId Plan ID
     * @return PlanInfo
     */
    public PlanInfo getPlanInfo(String planId) {
        PlanAggregate plan = planRepository.get(planId);
        if (plan == null) {
            return null;
        }
        return PlanInfo.from(plan);
    }

    /**
     * 获取 Plan 聚合
     *
     * @param planId Plan ID
     * @return Plan 聚合
     */
    public PlanAggregate getPlan(String planId) {
        return planRepository.get(planId);
    }

    /**
     * 生成 Plan ID（静态工具方法）
     *
     * @return Plan ID
     */
    public static String generatePlanId() {
        return "plan_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
}
