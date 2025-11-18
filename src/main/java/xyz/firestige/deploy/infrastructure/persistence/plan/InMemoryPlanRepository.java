package xyz.firestige.deploy.infrastructure.persistence.plan;

import xyz.firestige.deploy.domain.plan.PlanAggregate;
import xyz.firestige.deploy.domain.plan.PlanRepository;
import xyz.firestige.deploy.domain.state.PlanStateMachine;

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

    private final Map<String, PlanAggregate> plans = new ConcurrentHashMap<>();
    private final Map<String, PlanStateMachine> stateMachines = new ConcurrentHashMap<>();

    @Override
    public void save(PlanAggregate plan) {
        if (plan == null || plan.getPlanId() == null) {
            throw new IllegalArgumentException("Plan or PlanId cannot be null");
        }
        plans.put(plan.getPlanId(), plan);
    }

    @Override
    public void remove(String planId) {
        plans.remove(planId);
        stateMachines.remove(planId);
    }

    @Override
    public Optional<PlanAggregate> findById(String planId) {
        return Optional.ofNullable(plans.get(planId));
    }

    @Override
    public List<PlanAggregate> findAll() {
        return new ArrayList<>(plans.values());
    }

    @Override
    public void saveStateMachine(String planId, PlanStateMachine stateMachine) {
        stateMachines.put(planId, stateMachine);
    }

    @Override
    public Optional<PlanStateMachine> getStateMachine(String planId) {
        return Optional.ofNullable(stateMachines.get(planId));
    }
}

