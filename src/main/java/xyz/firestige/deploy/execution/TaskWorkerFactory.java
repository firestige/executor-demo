package xyz.firestige.deploy.execution;

import xyz.firestige.deploy.checkpoint.CheckpointService;
import xyz.firestige.deploy.domain.stage.TaskStage;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.event.TaskEventSink;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.support.conflict.ConflictRegistry;
import xyz.firestige.deploy.support.conflict.TenantConflictManager;

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
                        TenantConflictManager conflictManager);
}

