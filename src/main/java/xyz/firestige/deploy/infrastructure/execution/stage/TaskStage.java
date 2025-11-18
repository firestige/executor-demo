package xyz.firestige.deploy.infrastructure.execution.stage;

import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.StageResult;

import java.util.List;

/**
 * 新 Stage 接口：用于 Task 内部的服务切换动作（可多步骤）。
 */
public interface TaskStage {
    String getName();
    boolean canSkip(TaskRuntimeContext ctx);
    StageResult execute(TaskRuntimeContext ctx);
    void rollback(TaskRuntimeContext ctx);
    List<StageStep> getSteps();
}
