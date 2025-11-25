package xyz.firestige.deploy.domain.task;

public interface StateTransitionService {
    boolean transition(TaskAggregate aggregate, TaskStatus targetStatus, TaskRuntimeContext context);
    boolean canTransition(TaskAggregate aggregate, TaskStatus targetStatus, TaskRuntimeContext context);
}
