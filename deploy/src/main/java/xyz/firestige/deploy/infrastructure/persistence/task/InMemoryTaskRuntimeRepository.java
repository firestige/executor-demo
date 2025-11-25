package xyz.firestige.deploy.infrastructure.persistence.task;

import org.springframework.stereotype.Repository;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TaskRuntimeRepository;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现的 TaskRuntimeRepository
 * <p>
 * 用于存储 Task 的运行时状态（Executor、Context、Stages）
 */
public class InMemoryTaskRuntimeRepository implements TaskRuntimeRepository {

    private final Map<TaskId, TaskExecutor> executors = new ConcurrentHashMap<>();
    private final Map<TaskId, TaskRuntimeContext> contexts = new ConcurrentHashMap<>();
    private final Map<TaskId, List<TaskStage>> stages = new ConcurrentHashMap<>();

    @Override
    public void saveExecutor(TaskId taskId, TaskExecutor executor) {
        executors.put(taskId, executor);
    }

    @Override
    public Optional<TaskExecutor> getExecutor(TaskId taskId) {
        return Optional.ofNullable(executors.get(taskId));
    }

    @Override
    public void saveContext(TaskId taskId, TaskRuntimeContext context) {
        contexts.put(taskId, context);
    }

    @Override
    public Optional<TaskRuntimeContext> getContext(TaskId taskId) {
        return Optional.ofNullable(contexts.get(taskId));
    }

    @Override
    public void saveStages(TaskId taskId, List<TaskStage> stageList) {
        stages.put(taskId, stageList);
    }

    @Override
    public Optional<List<TaskStage>> getStages(TaskId taskId) {
        return Optional.ofNullable(stages.get(taskId));
    }

    @Override
    public void remove(TaskId taskId) {
        executors.remove(taskId);
        contexts.remove(taskId);
        stages.remove(taskId);
    }

    @Override
    public void removeExecutor(TaskId taskId) {
        executors.remove(taskId);
    }

    @Override
    public void removeContext(TaskId taskId) {
        contexts.remove(taskId);
    }

    @Override
    public void removeStages(TaskId taskId) {
        stages.remove(taskId);
    }
}

