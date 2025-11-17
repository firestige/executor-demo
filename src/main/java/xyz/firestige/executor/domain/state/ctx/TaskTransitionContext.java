package xyz.firestige.executor.domain.state.ctx;

import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;

/**
 * 任务状态迁移上下文：给 Guard / Action 使用，不暴露外部 DTO。
 */
public class TaskTransitionContext {
    private final TaskAggregate aggregate;
    private final TaskRuntimeContext context;
    private final int totalStages;

    public TaskTransitionContext(TaskAggregate aggregate, TaskRuntimeContext context, int totalStages) {
        this.aggregate = aggregate;
        this.context = context;
        this.totalStages = totalStages;
    }

    public TaskAggregate getAggregate() { return aggregate; }
    public TaskRuntimeContext getContext() { return context; }
    public int getTotalStages() { return totalStages; }
}
