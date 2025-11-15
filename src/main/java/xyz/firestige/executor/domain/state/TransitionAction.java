package xyz.firestige.executor.domain.state;

/**
 * 状态迁移动作，执行副作用（记录时间、指标、事件等）。
 */
public interface TransitionAction<C> {
    void onTransition(C context);
}

