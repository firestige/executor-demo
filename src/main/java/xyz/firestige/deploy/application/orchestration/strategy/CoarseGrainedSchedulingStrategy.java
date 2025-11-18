package xyz.firestige.deploy.application.orchestration.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.infrastructure.scheduling.ConflictRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 粗粒度调度策略
 * 
 * 特点：
 * - 创建时检查租户冲突，有任何重叠租户则立即拒绝创建
 * - 无租户重叠的 Plan 可以并发执行
 * - 适合对租户隔离要求严格的场景
 * 
 * 场景示例（有租户重叠）：
 * Plan-A: 租户 1,2,3 (运行中)
 * Plan-B: 租户 3,4,5 (尝试创建)
 * 结果：Plan-B 创建被拒绝（租户3冲突）
 * 
 * 场景示例（无租户重叠）：
 * Plan-A: 租户 1,2,3 (运行中)
 * Plan-C: 租户 4,5,6 (尝试创建)
 * 结果：Plan-C 创建成功，与 Plan-A 并发执行
 * 
 * @since Phase 18 - RF-12
 */
public class CoarseGrainedSchedulingStrategy implements PlanSchedulingStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(CoarseGrainedSchedulingStrategy.class);
    
    private final ConflictRegistry conflictRegistry;
    
    public CoarseGrainedSchedulingStrategy(ConflictRegistry conflictRegistry) {
        this.conflictRegistry = conflictRegistry;
        log.info("粗粒度调度策略初始化完成");
    }
    
    @Override
    public boolean canCreatePlan(List<String> tenantIds) {
        // 粗粒度策略：创建前检查所有租户
        List<String> conflictTenants = new ArrayList<>();
        for (String tenantId : tenantIds) {
            if (conflictRegistry.hasConflict(tenantId)) {
                conflictTenants.add(tenantId);
            }
        }
        
        if (!conflictTenants.isEmpty()) {
            log.warn("粗粒度策略：拒绝创建 Plan，冲突租户: {}", conflictTenants);
            return false;  // 立即拒绝，不等待
        }
        
        log.debug("粗粒度策略：允许创建 Plan，所有租户无冲突（租户数量: {}）", tenantIds.size());
        return true;
    }
    
    @Override
    public boolean canStartPlan(String planId, List<String> tenantIds) {
        // 启动时再次检查（双重保险）
        List<String> conflictTenants = new ArrayList<>();
        for (String tenantId : tenantIds) {
            if (conflictRegistry.hasConflict(tenantId)) {
                conflictTenants.add(tenantId);
            }
        }
        
        if (!conflictTenants.isEmpty()) {
            log.warn("粗粒度策略：拒绝启动 Plan {}，冲突租户: {}", planId, conflictTenants);
            return false;  // 立即拒绝
        }
        
        log.info("粗粒度策略：允许启动 Plan {}，所有租户无冲突", planId);
        return true;
    }
    
    @Override
    public void onPlanCreated(String planId, List<String> tenantIds) {
        log.debug("粗粒度策略：Plan {} 创建完成，租户列表: {}", planId, tenantIds);
    }
    
    @Override
    public void onPlanCompleted(String planId, List<String> tenantIds) {
        log.debug("粗粒度策略：Plan {} 完成，租户锁由 ConflictRegistry 释放", planId);
    }
}

