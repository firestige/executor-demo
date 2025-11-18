package xyz.firestige.deploy.domain.state;

/**
 * 通用状态迁移动作接口。
 */
public interface TransitionAction<T> {
    void onTransition(T ctx);
}

