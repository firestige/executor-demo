package xyz.firestige.deploy.infrastructure.execution;

/**
 * Encapsulate TaskExecutor creation and wiring.
 * <p>
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
}

