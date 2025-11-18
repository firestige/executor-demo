package xyz.firestige.deploy.orchestration.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.support.conflict.ConflictRegistry;

import java.util.List;

/**
 * 细粒度调度策略（默认策略）
 *
 * 特点：
 * - 只检查租户级冲突
 * - 不同 Plan 的 Task 可以并发执行
 * - 高吞吐量，适合生产环境
 *
 * 场景示例：
 * Plan-A: 租户 1,2,3 (运行中)
 * Plan-B: 租户 3,4,5 (尝试启动)
 * 结果：Plan-B 可以启动，租户 4,5 正常执行，租户 3 被跳过
 *
 * @since Phase 18 - RF-12
 */
public class FineGrainedSchedulingStrategy implements PlanSchedulingStrategy {

    private static final Logger log = LoggerFactory.getLogger(FineGrainedSchedulingStrategy.class);

    private final ConflictRegistry conflictRegistry;

    public FineGrainedSchedulingStrategy(ConflictRegistry conflictRegistry) {
        this.conflictRegistry = conflictRegistry;
    }

    @Override
    public boolean canCreatePlan(List<String> tenantIds) {
        // 细粒度策略：创建时不检查冲突，允许并发创建
        log.debug("细粒度策略：允许创建 Plan，租户数量: {}", tenantIds.size());
        return true;
    }

    @Override
    public boolean canStartPlan(String planId, List<String> tenantIds) {
        // 细粒度策略：启动时检查租户冲突，但不阻止 Plan 启动
        int conflictCount = 0;
        for (String tenantId : tenantIds) {
            if (conflictRegistry.hasConflict(tenantId)) {
                conflictCount++;
                log.warn("租户 {} 存在冲突，Plan {} 的该租户任务将被跳过", tenantId, planId);
            }
        }

        if (conflictCount > 0) {
            log.info("Plan {} 存在 {} 个租户冲突，但仍允许启动（冲突任务将被跳过）",
                    planId, conflictCount);
        }

        // 始终返回 true，冲突检测由 PlanOrchestrator 在提交时处理
        return true;
    }

    @Override
    public void onPlanCreated(String planId, List<String> tenantIds) {
        // 无需特殊处理
        log.debug("Plan {} 创建完成，租户数量: {}", planId, tenantIds.size());
    }

    @Override
    public void onPlanCompleted(String planId, List<String> tenantIds) {
        // 释放租户锁（由 ConflictRegistry 和 PlanOrchestrator 处理）
        log.debug("Plan {} 完成，租户锁将由 ConflictRegistry 释放", planId);
    }
}

