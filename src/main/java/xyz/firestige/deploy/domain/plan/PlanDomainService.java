package xyz.firestige.deploy.domain.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.config.ExecutorProperties;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.state.TaskStateManager;

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
    // ✅ RF-11 改进版: 使用领域事件发布器接口（支持多种实现）
    private final DomainEventPublisher domainEventPublisher;

    public PlanDomainService(
            PlanRepository planRepository,
            DomainEventPublisher domainEventPublisher) {
        this.planRepository = planRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 创建 Plan 聚合（只创建 Plan，不创建 Task）
     * DDD 重构：简化为查询和持久化
     *
     * @param planId Plan ID
     * @param tenantCount 租户数量（暂未使用，保留接口兼容）
     * @return Plan 聚合
     */
    public PlanAggregate createPlan(PlanId planId, int tenantCount, int maxConcurrency) {
        logger.info("[PlanDomainService] 创建 Plan: {}, 租户数量: {}", planId, tenantCount);

        // ✅ 创建 Plan 聚合（业务逻辑在构造函数中）
        PlanAggregate plan = new PlanAggregate(planId);
        plan.setMaxConcurrency(maxConcurrency);

        // ✅ 标记为 READY（业务逻辑稍后在应用层调用）
        // 注意：此时 tasks 为空，需要先添加 Task 再 markAsReady()

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
    public void addTaskToPlan(PlanId planId, TaskId taskId) {
        logger.debug("[PlanDomainService] 添加 Task 到 Plan: {} -> {}", planId, taskId);

        PlanAggregate plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan 不存在: " + planId));

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
    public void markPlanAsReady(PlanId planId) {
        logger.info("[PlanDomainService] 标记 Plan 为 READY: {}", planId);

        PlanAggregate plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan 不存在: " + planId));

        // ✅ 调用聚合的业务方法
        plan.markAsReady();

        // 更新状态机并发布事件
        updatePlanStateAndPublishEvent(plan);

        logger.info("[PlanDomainService] Plan 已标记为 READY: {}", planId);
    }

    /**
     * 启动 Plan 执行
     * DDD 重构：调用聚合的业务方法
     *
     * @param planId Plan ID
     */
    public void startPlan(PlanId planId) {
        logger.info("[PlanDomainService] 启动 Plan 执行: {}", planId);

        PlanAggregate plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan 不存在: " + planId));

        // ✅ 调用聚合的业务方法（不变式保护在聚合内部）
        plan.start();

        // 更新状态机并发布事件
        updatePlanStateAndPublishEvent(plan);

        logger.info("[PlanDomainService] Plan 已启动: {}", planId);
    }

    /**
     * 暂停 Plan 执行
     * DDD 重构：调用聚合的业务方法
     *
     * @param planId Plan ID
     */
    public void pausePlanExecution(PlanId planId) {
        logger.info("[PlanDomainService] 暂停 Plan 执行: {}", planId);

        PlanAggregate plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan 不存在: " + planId));

        // ✅ 调用聚合的业务方法（不变式保护在聚合内部）
        plan.pause();

        // 更新状态机并发布事件
        updatePlanStateAndPublishEvent(plan);

        logger.info("[PlanDomainService] Plan 已暂停: {}", planId);
    }

    /**
     * 恢复 Plan 执行
     * DDD 重构：调用聚合的业务方法
     *
     * @param planId Plan ID
     */
    public void resumePlanExecution(PlanId planId) {
        logger.info("[PlanDomainService] 恢复 Plan 执行: {}", planId);

        PlanAggregate plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan 不存在: " + planId));

        // ✅ 调用聚合的业务方法（不变式保护在聚合内部）
        plan.resume();

        // 更新状态机并发布事件
        updatePlanStateAndPublishEvent(plan);

        logger.info("[PlanDomainService] Plan 已恢复: {}", planId);
    }

    private void updatePlanStateAndPublishEvent(PlanAggregate plan) {
        planRepository.save(plan);

        // ✅ RF-11: 提取并发布聚合产生的领域事件
        domainEventPublisher.publishAll(plan.getDomainEvents());
        plan.clearDomainEvents();
    }

    /**
     * 获取 Plan 聚合
     *
     * @param planId Plan ID
     * @return Plan 聚合
     */
    public PlanAggregate getPlan(PlanId planId) {
        return planRepository.findById(planId).orElse(null);
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
