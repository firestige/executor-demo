package xyz.firestige.executor.domain.stage;

import xyz.firestige.executor.domain.task.TaskContext;

import java.util.List;

/**
 * 新 Stage 接口：用于 Task 内部的服务切换动作（可多步骤）。
 */
public interface TaskStage {
    String getName();
    boolean canSkip(TaskContext ctx);
    StageExecutionResult execute(TaskContext ctx);
    void rollback(TaskContext ctx);
    List<StageStep> getSteps();
}

