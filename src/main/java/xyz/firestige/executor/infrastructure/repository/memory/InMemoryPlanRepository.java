package xyz.firestige.executor.infrastructure.repository.memory;

import xyz.firestige.executor.domain.plan.PlanAggregate;
import xyz.firestige.executor.domain.plan.PlanRepository;
import xyz.firestige.executor.domain.plan.PlanStatus;
import xyz.firestige.executor.domain.state.PlanStateMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plan Repository 内存实现
 *
 * 使用 ConcurrentHashMap 存储 Plan
 *
 * 注意：
 * - 生产环境应替换为 Redis 或数据库实现
 * - 当前实现不支持持久化和集群
 *
 * @since DDD 重构
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
    public PlanAggregate get(String planId) {
        return plans.get(planId);
    }

    @Override
    public List<PlanAggregate> findAll() {
        return new ArrayList<>(plans.values());
    }

    @Override
    public void remove(String planId) {
        plans.remove(planId);
        stateMachines.remove(planId);
    }

    @Override
    public void updateStatus(String planId, PlanStatus status) {
        PlanAggregate plan = plans.get(planId);
        if (plan != null) {
            plan.setStatus(status);
        }
    }

    @Override
    public void saveStateMachine(String planId, PlanStateMachine stateMachine) {
        stateMachines.put(planId, stateMachine);
    }

    @Override
    public PlanStateMachine getStateMachine(String planId) {
        return stateMachines.get(planId);
    }
}

