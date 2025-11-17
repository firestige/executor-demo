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
 *
 * RF-02: Added context-based create method to simplify parameter passing.
 */
public interface TaskWorkerFactory {

    /**
     * Create TaskExecutor with creation context (RF-02 recommended approach)
     *
     * @param context encapsulates all parameters needed for TaskExecutor creation
     * @return configured TaskExecutor instance
     */
    TaskExecutor create(TaskWorkerCreationContext context);

    /**
     * Create TaskExecutor with individual parameters (legacy method, kept for backward compatibility)
     *
     * @deprecated Use {@link #create(TaskWorkerCreationContext)} instead for better readability
     */
    @Deprecated
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

