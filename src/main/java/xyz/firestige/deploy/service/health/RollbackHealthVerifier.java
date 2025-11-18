package xyz.firestige.deploy.service.health;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;

/**
 * Verify that rollback has restored system to a healthy state.
 * Production implementation may query service health endpoints or internal signals.
 */
public interface RollbackHealthVerifier {
    boolean verify(TaskAggregate aggregate, TaskRuntimeContext context);
}
