package xyz.firestige.deploy.infrastructure.persistence.projection.memory;

import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjection;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjectionStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory PlanStateProjectionStore (fallback)
 */
public class InMemoryPlanStateProjectionStore implements PlanStateProjectionStore {
    private final Map<String, PlanStateProjection> store = new ConcurrentHashMap<>();

    @Override
    public void save(PlanStateProjection projection) {
        if (projection == null || projection.getPlanId() == null) return;
        store.put(projection.getPlanId().getValue(), projection);
    }

    @Override
    public PlanStateProjection load(PlanId planId) {
        if (planId == null) return null;
        return store.get(planId.getValue());
    }

    @Override
    public void remove(PlanId planId) {
        if (planId == null) return;
        store.remove(planId.getValue());
    }
}

