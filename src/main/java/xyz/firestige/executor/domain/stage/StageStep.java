package xyz.firestige.executor.domain.stage;

import xyz.firestige.executor.domain.task.TaskRuntimeContext;

/**
 * Stage 内部的单个步骤定义（不再包含 rollback）。
 */
public interface StageStep {
    String getStepName();
    void execute(TaskRuntimeContext ctx) throws Exception;
}
