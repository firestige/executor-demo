package xyz.firestige.executor.service.health;

import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;

/**
 * Default stub: always returns true. Replace with real verifier when integrating.
 */
public class AlwaysTrueRollbackHealthVerifier implements RollbackHealthVerifier {
    @Override
    public boolean verify(TaskAggregate aggregate, TaskRuntimeContext context) {
        return true;
    }
}
