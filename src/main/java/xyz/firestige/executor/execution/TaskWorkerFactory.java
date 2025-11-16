package xyz.firestige.executor.execution;

import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.event.TaskEventSink;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.support.conflict.ConflictRegistry;

import java.util.List;

/**
 * Encapsulate TaskExecutor creation and wiring.
 */
public interface TaskWorkerFactory {
    TaskExecutor create(String planId,
                        TaskAggregate task,
                        List<TaskStage> stages,
                        TaskRuntimeContext ctx,
                        CheckpointService checkpointService,
                        TaskEventSink eventSink,
                        int progressIntervalSeconds,
                        TaskStateManager stateManager,
                        ConflictRegistry conflicts);
}

