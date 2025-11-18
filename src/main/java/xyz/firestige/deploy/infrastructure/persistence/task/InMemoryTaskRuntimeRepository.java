package xyz.firestige.deploy.infrastructure.persistence.task;

import org.springframework.stereotype.Repository;
import xyz.firestige.deploy.domain.stage.TaskStage;
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
@Repository
public class InMemoryTaskRuntimeRepository implements TaskRuntimeRepository {

    private final Map<String, TaskExecutor> executors = new ConcurrentHashMap<>();
    private final Map<String, TaskRuntimeContext> contexts = new ConcurrentHashMap<>();
    private final Map<String, List<TaskStage>> stages = new ConcurrentHashMap<>();

    @Override
    public void saveExecutor(String taskId, TaskExecutor executor) {
        executors.put(taskId, executor);
    }

    @Override
    public Optional<TaskExecutor> getExecutor(String taskId) {
        return Optional.ofNullable(executors.get(taskId));
    }

    @Override
    public void saveContext(String taskId, TaskRuntimeContext context) {
        contexts.put(taskId, context);
    }

    @Override
    public Optional<TaskRuntimeContext> getContext(String taskId) {
        return Optional.ofNullable(contexts.get(taskId));
    }

    @Override
    public void saveStages(String taskId, List<TaskStage> stageList) {
        stages.put(taskId, stageList);
    }

    @Override
    public Optional<List<TaskStage>> getStages(String taskId) {
        return Optional.ofNullable(stages.get(taskId));
    }

    @Override
    public void remove(String taskId) {
        executors.remove(taskId);
        contexts.remove(taskId);
        stages.remove(taskId);
    }

    @Override
    public void removeExecutor(String taskId) {
        executors.remove(taskId);
    }

    @Override
    public void removeContext(String taskId) {
        contexts.remove(taskId);
    }

    @Override
    public void removeStages(String taskId) {
        stages.remove(taskId);
    }
}

