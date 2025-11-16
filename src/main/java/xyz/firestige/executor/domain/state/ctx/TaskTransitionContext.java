package xyz.firestige.executor.domain.state.ctx;

import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskContext;

/**
 * 任务状态迁移上下文：给 Guard / Action 使用，不暴露外部 DTO。
 */
public class TaskTransitionContext {
    private final TaskAggregate aggregate;
    private final TaskContext context;
    private final int totalStages;

    public TaskTransitionContext(TaskAggregate aggregate, TaskContext context, int totalStages) {
        this.aggregate = aggregate;
        this.context = context;
        this.totalStages = totalStages;
    }

    public TaskAggregate getAggregate() { return aggregate; }
    public TaskContext getContext() { return context; }
    public int getTotalStages() { return totalStages; }
}

