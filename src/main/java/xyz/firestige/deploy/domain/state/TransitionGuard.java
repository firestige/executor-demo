package xyz.firestige.deploy.domain.state;

/**
 * 通用状态迁移 Guard 接口。
 */
public interface TransitionGuard<T> {
    boolean canTransition(T ctx);
}

