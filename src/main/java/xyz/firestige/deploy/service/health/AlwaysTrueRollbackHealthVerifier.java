package xyz.firestige.deploy.service.health;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;

/**
 * Default stub: always returns true. Replace with real verifier when integrating.
 */
public class AlwaysTrueRollbackHealthVerifier implements RollbackHealthVerifier {
    @Override
    public boolean verify(TaskAggregate aggregate, TaskRuntimeContext context) {
        return true;
    }
}
