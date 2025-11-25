package xyz.firestige.deploy.application.orchestration.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRepository;
import xyz.firestige.deploy.domain.plan.event.PlanCompletedEvent;
import xyz.firestige.deploy.domain.plan.event.PlanFailedEvent;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Plan 完成事件监听器（重构版）
 * <p>
 * 职责：监听 Plan 完成/失败事件，释放租户冲突锁
 * <p>
 * 重构说明（RF-14）：
 * - 旧架构：PlanSchedulingStrategy（策略接口）+ ConflictRegistry（锁管理）
 * - 新架构：TenantConflictManager（统一管理策略和锁）
 * - 收益：代码简化，去除抽象层
 *
 * @since Phase 18 - RF-12
 * @updated RF-14 重构为直接使用 TenantConflictManager
 */
@Component
public class PlanCompletionListener {

    private static final Logger logger = LoggerFactory.getLogger(PlanCompletionListener.class);

    private final TenantConflictManager conflictManager;
    private final TaskRepository taskRepository;

    public PlanCompletionListener(
            TenantConflictManager conflictManager,
            TaskRepository taskRepository) {
        this.conflictManager = conflictManager;
        this.taskRepository = taskRepository;
    }

    /**
     * 处理 Plan 完成事件
     * <p>
     * 事件流程：PlanAggregate.complete() → PlanDomainService.publishEvent() → 本方法
     *
     * @param event Plan 完成事件
     */
    @EventListener
    public void onPlanCompleted(PlanCompletedEvent event) {
        PlanId planId = event.getPlanId();
        logger.info("[PlanCompletionListener] 收到 Plan 完成事件: {}", planId);

        try {
            // 1. 查询 Plan 包含的所有租户
            List<TaskAggregate> tasks = taskRepository.findByPlanId(planId);
            List<TenantId> tenantIds = tasks.stream()
                    .map(TaskAggregate::getTenantId)
                    .collect(Collectors.toList());

            if (tenantIds.isEmpty()) {
                logger.warn("[PlanCompletionListener] Plan {} 没有关联任务，跳过锁释放", planId);
                return;
            }

            logger.info("[PlanCompletionListener] Plan {} 包含 {} 个租户: {}",
                    planId, tenantIds.size(), tenantIds);

            // 2. 释放所有租户锁
            for (TenantId tenantId : tenantIds) {
                conflictManager.releaseTask(tenantId);
            }

            logger.info("[PlanCompletionListener] Plan {} 的所有租户锁已释放", planId);

        } catch (Exception e) {
            logger.error("[PlanCompletionListener] 处理 Plan 完成事件失败: {}", planId, e);
        }
    }

    /**
     * 处理 Plan 失败事件
     * <p>
     * 事件流程：PlanAggregate.fail() → PlanDomainService.publishEvent() → 本方法
     *
     * @param event Plan 失败事件
     */
    @EventListener
    public void onPlanFailed(PlanFailedEvent event) {
        PlanId planId = event.getPlanId();
        logger.info("[PlanCompletionListener] 收到 Plan 失败事件: {}, 原因: {}",
                planId, event.getFailureSummary());

        try {
            // 1. 查询 Plan 包含的所有租户
            List<TaskAggregate> tasks = taskRepository.findByPlanId(planId);
            List<TenantId> tenantIds = tasks.stream()
                    .map(TaskAggregate::getTenantId)
                    .collect(Collectors.toList());

            if (tenantIds.isEmpty()) {
                logger.warn("[PlanCompletionListener] Plan {} 没有关联任务，跳过锁释放", planId);
                return;
            }

            logger.info("[PlanCompletionListener] Plan {} 失败，包含 {} 个租户: {}",
                    planId, tenantIds.size(), tenantIds);

            // 2. 释放所有租户锁
            for (TenantId tenantId : tenantIds) {
                conflictManager.releaseTask(tenantId);
            }

            logger.info("[PlanCompletionListener] Plan {} 的所有租户锁已释放", planId);

        } catch (Exception e) {
            logger.error("[PlanCompletionListener] 处理 Plan 失败事件失败: {}", planId, e);
        }
    }
}

