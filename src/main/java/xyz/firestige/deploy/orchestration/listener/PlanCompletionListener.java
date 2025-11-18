package xyz.firestige.deploy.orchestration.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRepository;
import xyz.firestige.deploy.orchestration.strategy.PlanSchedulingStrategy;
import xyz.firestige.deploy.state.event.plan.PlanCompletedEvent;
import xyz.firestige.deploy.state.event.plan.PlanFailedEvent;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Plan 完成事件监听器（RF-12: 调度策略集成）
 * <p>
 * 职责：监听 Plan 完成/失败事件，通知调度策略清理冲突标记
 * <p>
 * 设计说明：
 * - 监听 PlanCompletedEvent 和 PlanFailedEvent
 * - 查询 Plan 包含的所有租户 ID
 * - 调用 schedulingStrategy.onPlanCompleted() 清理 ConflictRegistry
 * - 异步执行，不影响主流程
 *
 * @since Phase 18 - RF-12
 */
@Component
public class PlanCompletionListener {

    private static final Logger logger = LoggerFactory.getLogger(PlanCompletionListener.class);

    private final PlanSchedulingStrategy schedulingStrategy;
    private final TaskRepository taskRepository;

    public PlanCompletionListener(
            PlanSchedulingStrategy schedulingStrategy,
            TaskRepository taskRepository) {
        this.schedulingStrategy = schedulingStrategy;
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
    public void handlePlanCompleted(PlanCompletedEvent event) {
        String planId = event.getPlanId();
        logger.info("[PlanCompletionListener] 收到 Plan 完成事件: {}", planId);

        try {
            // 1. 查询 Plan 包含的所有任务
            List<TaskAggregate> tasks = taskRepository.findByPlanId(planId);

            // 2. 提取租户 ID 列表
            List<String> tenantIds = tasks.stream()
                .map(TaskAggregate::getTenantId)
                .distinct()
                .collect(Collectors.toList());

            logger.info("[PlanCompletionListener] Plan {} 包含 {} 个租户: {}",
                planId, tenantIds.size(), tenantIds);

            // 3. 通知调度策略：Plan 已完成，清理冲突标记
            schedulingStrategy.onPlanCompleted(planId, tenantIds);

            logger.info("[PlanCompletionListener] 调度策略已清理 Plan {} 的冲突标记", planId);

        } catch (Exception e) {
            // ⚠️ 监听器异常不应影响主流程，仅记录日志
            logger.error("[PlanCompletionListener] 处理 Plan 完成事件失败: {}", planId, e);
        }
    }

    /**
     * 处理 Plan 失败事件
     * <p>
     * 事件流程：PlanAggregate.markAsFailed() → PlanDomainService.publishEvent() → 本方法
     * <p>
     * 注意：即使 Plan 失败，也需要清理冲突标记，允许后续 Plan 创建
     *
     * @param event Plan 失败事件
     */
    @EventListener
    public void handlePlanFailed(PlanFailedEvent event) {
        String planId = event.getPlanId();
        logger.info("[PlanCompletionListener] 收到 Plan 失败事件: {}, 原因: {}",
            planId, event.getFailureSummary());

        try {
            // 1. 查询 Plan 包含的所有任务
            List<TaskAggregate> tasks = taskRepository.findByPlanId(planId);

            // 2. 提取租户 ID 列表
            List<String> tenantIds = tasks.stream()
                .map(TaskAggregate::getTenantId)
                .distinct()
                .collect(Collectors.toList());

            logger.info("[PlanCompletionListener] Plan {} 失败，包含 {} 个租户: {}",
                planId, tenantIds.size(), tenantIds);

            // 3. 通知调度策略：Plan 已完成（失败也算完成），清理冲突标记
            schedulingStrategy.onPlanCompleted(planId, tenantIds);

            logger.info("[PlanCompletionListener] 调度策略已清理 Plan {} 的冲突标记", planId);

        } catch (Exception e) {
            // ⚠️ 监听器异常不应影响主流程，仅记录日志
            logger.error("[PlanCompletionListener] 处理 Plan 失败事件失败: {}", planId, e);
        }
    }
}
