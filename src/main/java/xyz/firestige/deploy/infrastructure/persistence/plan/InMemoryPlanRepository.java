package xyz.firestige.deploy.infrastructure.persistence.plan;

import xyz.firestige.deploy.domain.plan.PlanAggregate;
import xyz.firestige.deploy.domain.plan.PlanInfo;
import xyz.firestige.deploy.domain.plan.PlanRepository;
import xyz.firestige.deploy.domain.shared.vo.PlanId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plan Repository 内存实现（DDD 重构：简化方案）
 *
 * 使用 ConcurrentHashMap 存储 Plan 聚合根
 *
 * 注意：
 * - 只管理聚合根
 * - 生产环境应替换为 Redis 或数据库实现
 *
 * @since DDD 重构 Phase 18 - RF-09 简化方案
 */
public class InMemoryPlanRepository implements PlanRepository {

    private final Map<PlanId, PlanAggregate> plans = new ConcurrentHashMap<>();

    @Override
    public void save(PlanAggregate plan) {
        if (plan == null || plan.getPlanId() == null) {
            throw new IllegalArgumentException("Plan or PlanId cannot be null");
        }
        plans.put(plan.getPlanId(), plan);
    }

    @Override
    public void remove(PlanId planId) {
        plans.remove(planId);
    }

    @Override
    public Optional<PlanAggregate> findById(PlanId planId) {
        return Optional.ofNullable(plans.get(planId));
    }

    @Override
    public List<PlanAggregate> findAll() {
        return new ArrayList<>(plans.values());
    }
}

