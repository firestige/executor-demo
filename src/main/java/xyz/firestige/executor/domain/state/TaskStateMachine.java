package xyz.firestige.executor.domain.state;

import xyz.firestige.executor.domain.task.TaskContext;
import xyz.firestige.executor.domain.state.ctx.TaskTransitionContext;
import xyz.firestige.executor.state.TaskStatus;

import java.util.*;

/**
 * 新 Task 状态机（不使用 V2 命名），带 Guard/Action 扩展。
 * 暂不接线旧流程，后续 Phase 接线。
 */
public class TaskStateMachine {

    private TaskStatus current;

    private final Map<TaskStatus, Set<TaskStatus>> rules = new EnumMap<>(TaskStatus.class);
    private final Map<String, List<TransitionGuard<TaskContext>>> guards = new HashMap<>();
    private final Map<String, List<TransitionAction<TaskContext>>> actions = new HashMap<>();
    private final Map<String, List<TransitionGuard<TaskTransitionContext>>> guardsCtx = new HashMap<>();
    private final Map<String, List<TransitionAction<TaskTransitionContext>>> actionsCtx = new HashMap<>();

    public TaskStateMachine(TaskStatus initial) {
        this.current = initial;
        initRules();
    }

    private void initRules() {
        rules.put(TaskStatus.CREATED, EnumSet.of(TaskStatus.VALIDATING));
        rules.put(TaskStatus.VALIDATING, EnumSet.of(TaskStatus.VALIDATION_FAILED, TaskStatus.PENDING));
        rules.put(TaskStatus.PENDING, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED));
        rules.put(TaskStatus.RUNNING, EnumSet.of(TaskStatus.PAUSED, TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.ROLLING_BACK));
        rules.put(TaskStatus.PAUSED, EnumSet.of(TaskStatus.RESUMING, TaskStatus.ROLLING_BACK, TaskStatus.CANCELLED));
        rules.put(TaskStatus.RESUMING, EnumSet.of(TaskStatus.RUNNING));
        rules.put(TaskStatus.COMPLETED, EnumSet.of(TaskStatus.ROLLING_BACK));
        rules.put(TaskStatus.FAILED, EnumSet.of(TaskStatus.ROLLING_BACK, TaskStatus.RUNNING));
        rules.put(TaskStatus.ROLLING_BACK, EnumSet.of(TaskStatus.ROLLED_BACK, TaskStatus.ROLLBACK_FAILED));
        rules.put(TaskStatus.ROLLED_BACK, EnumSet.noneOf(TaskStatus.class));
        rules.put(TaskStatus.ROLLBACK_FAILED, EnumSet.of(TaskStatus.ROLLING_BACK));
        rules.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));
        rules.put(TaskStatus.VALIDATION_FAILED, EnumSet.noneOf(TaskStatus.class));
    }

    private String key(TaskStatus from, TaskStatus to) { return from.name()+"->"+to.name(); }

    public void registerGuard(TaskStatus from, TaskStatus to, TransitionGuard<TaskContext> guard) {
        guards.computeIfAbsent(key(from,to), k-> new ArrayList<>()).add(guard);
    }

    public void registerAction(TaskStatus from, TaskStatus to, TransitionAction<TaskContext> action) {
        actions.computeIfAbsent(key(from,to), k-> new ArrayList<>()).add(action);
    }

    public void registerGuard(TaskStatus from, TaskStatus to, TransitionGuard<TaskTransitionContext> guard) {
        guardsCtx.computeIfAbsent(key(from,to), k-> new ArrayList<>()).add(guard);
    }

    public void registerAction(TaskStatus from, TaskStatus to, TransitionAction<TaskTransitionContext> action) {
        actionsCtx.computeIfAbsent(key(from,to), k-> new ArrayList<>()).add(action);
    }

    public synchronized boolean canTransition(TaskStatus to, TaskContext ctx) {
        Set<TaskStatus> allowed = rules.getOrDefault(current, Collections.emptySet());
        if (!allowed.contains(to)) return false;
        List<TransitionGuard<TaskContext>> gs = guards.get(key(current,to));
        if (gs != null) {
            for (TransitionGuard<TaskContext> g : gs) {
                if (!g.canTransition(ctx)) return false;
            }
        }
        return true;
    }

    public synchronized TaskStatus transitionTo(TaskStatus to, TaskContext ctx) {
        if (!canTransition(to, ctx)) {
            return current; // 拒绝非法或不满足 Guard 的迁移
        }
        TaskStatus old = current;
        current = to;
        List<TransitionAction<TaskContext>> as = actions.get(key(old,to));
        if (as != null) {
            for (TransitionAction<TaskContext> a : as) a.onTransition(ctx);
        }
        return current;
    }

    public synchronized boolean canTransition(TaskStatus to, TaskTransitionContext ctx) {
        Set<TaskStatus> allowed = rules.getOrDefault(current, Collections.emptySet());
        if (!allowed.contains(to)) return false;
        List<TransitionGuard<TaskTransitionContext>> gs = guardsCtx.get(key(current,to));
        if (gs != null) {
            for (TransitionGuard<TaskTransitionContext> g : gs) {
                if (!g.canTransition(ctx)) return false;
            }
        }
        return true;
    }

    public synchronized TaskStatus transitionTo(TaskStatus to, TaskTransitionContext ctx) {
        if (!canTransition(to, ctx)) return current;
        TaskStatus old = current;
        current = to;
        List<TransitionAction<TaskTransitionContext>> as = actionsCtx.get(key(old,to));
        if (as != null) {
            for (TransitionAction<TaskTransitionContext> a : as) a.onTransition(ctx);
        }
        return current;
    }

    public TaskStatus getCurrent() { return current; }
}
