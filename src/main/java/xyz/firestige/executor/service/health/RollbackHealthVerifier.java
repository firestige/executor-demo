package xyz.firestige.executor.service.health;

import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;

/**
 * Verify that rollback has restored system to a healthy state.
 * Production implementation may query service health endpoints or internal signals.
 */
public interface RollbackHealthVerifier {
    boolean verify(TaskAggregate aggregate, TaskRuntimeContext context);
}
