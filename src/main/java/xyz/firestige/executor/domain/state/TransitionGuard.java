package xyz.firestige.executor.domain.state;

/**
 * 状态迁移 Guard，返回是否允许迁移。
 */
public interface TransitionGuard<C> {
    boolean canTransition(C context);
}

