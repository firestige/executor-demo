package xyz.firestige.executor.domain.state;

/**
 * 通用状态迁移动作接口。
 */
public interface TransitionAction<T> {
    void onTransition(T ctx);
}

