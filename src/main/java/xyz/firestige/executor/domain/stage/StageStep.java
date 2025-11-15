package xyz.firestige.executor.domain.stage;

import xyz.firestige.executor.domain.task.TaskContext;

/**
 * Stage 内部的单个步骤定义。
 */
public interface StageStep {
    String getStepName();
    void execute(TaskContext ctx) throws Exception;
    void rollback(TaskContext ctx) throws Exception;
}

