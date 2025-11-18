package xyz.firestige.deploy.domain.state;

import xyz.firestige.deploy.domain.plan.PlanContext;
import xyz.firestige.deploy.domain.plan.PlanStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plan 状态机（新实现，不使用 V2 命名）。
 */
public class PlanStateMachine {

    private PlanStatus current;

    private final Map<PlanStatus, Set<PlanStatus>> rules = new EnumMap<>(PlanStatus.class);
    private final Map<String, List<TransitionGuard<PlanContext>>> guards = new HashMap<>();
    private final Map<String, List<TransitionAction<PlanContext>>> actions = new HashMap<>();

    public PlanStateMachine(PlanStatus initial) {
        this.current = initial;
        initRules();
    }

    private void initRules() {
        rules.put(PlanStatus.CREATED, EnumSet.of(PlanStatus.VALIDATING));
        rules.put(PlanStatus.VALIDATING, EnumSet.of(PlanStatus.READY, PlanStatus.FAILED));
        rules.put(PlanStatus.READY, EnumSet.of(PlanStatus.RUNNING, PlanStatus.CANCELLED));
        rules.put(PlanStatus.RUNNING, EnumSet.of(PlanStatus.PAUSED, PlanStatus.PARTIAL_FAILED, PlanStatus.COMPLETED, PlanStatus.ROLLING_BACK));
        rules.put(PlanStatus.PAUSED, EnumSet.of(PlanStatus.RUNNING, PlanStatus.CANCELLED));
        rules.put(PlanStatus.PARTIAL_FAILED, EnumSet.of(PlanStatus.RUNNING, PlanStatus.ROLLING_BACK, PlanStatus.FAILED));
        rules.put(PlanStatus.ROLLING_BACK, EnumSet.of(PlanStatus.ROLLED_BACK, PlanStatus.FAILED));
        rules.put(PlanStatus.COMPLETED, EnumSet.of(PlanStatus.ROLLING_BACK));
        rules.put(PlanStatus.ROLLED_BACK, EnumSet.noneOf(PlanStatus.class));
        rules.put(PlanStatus.FAILED, EnumSet.noneOf(PlanStatus.class));
        rules.put(PlanStatus.CANCELLED, EnumSet.noneOf(PlanStatus.class));
    }

    private String key(PlanStatus from, PlanStatus to) { return from.name()+"->"+to.name(); }

    public void registerGuard(PlanStatus from, PlanStatus to, TransitionGuard<PlanContext> guard) {
        guards.computeIfAbsent(key(from,to), k-> new ArrayList<>()).add(guard);
    }

    public void registerAction(PlanStatus from, PlanStatus to, TransitionAction<PlanContext> action) {
        actions.computeIfAbsent(key(from,to), k-> new ArrayList<>()).add(action);
    }

    public synchronized boolean canTransition(PlanStatus to, PlanContext ctx) {
        Set<PlanStatus> allowed = rules.getOrDefault(current, Collections.emptySet());
        if (!allowed.contains(to)) return false;
        List<TransitionGuard<PlanContext>> gs = guards.get(key(current,to));
        if (gs != null) {
            for (TransitionGuard<PlanContext> g : gs) {
                if (!g.canTransition(ctx)) return false;
            }
        }
        return true;
    }

    public synchronized PlanStatus transitionTo(PlanStatus to, PlanContext ctx) {
        if (!canTransition(to, ctx)) {
            return current;
        }
        PlanStatus old = current;
        current = to;
        List<TransitionAction<PlanContext>> as = actions.get(key(old,to));
        if (as != null) {
            for (TransitionAction<PlanContext> a : as) a.onTransition(ctx);
        }
        return current;
    }

    public PlanStatus getCurrent() { return current; }
}
