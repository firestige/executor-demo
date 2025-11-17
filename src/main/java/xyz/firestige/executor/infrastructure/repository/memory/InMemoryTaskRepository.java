package xyz.firestige.executor.infrastructure.repository.memory;

import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRepository;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.execution.TaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Task Repository 内存实现
 *
 * 使用 ConcurrentHashMap 存储 Task 及其运行时状态
 *
 * 注意：
 * - 生产环境应替换为 Redis 或数据库实现
 * - 当前实现不支持持久化和集群
 *
 * @since DDD 重构
 */
public class InMemoryTaskRepository implements TaskRepository {

    private final Map<String, TaskAggregate> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<TaskStage>> stages = new ConcurrentHashMap<>();
    private final Map<String, TaskRuntimeContext> contexts = new ConcurrentHashMap<>();
    private final Map<String, TaskExecutor> executors = new ConcurrentHashMap<>();

    // 控制标志
    private final Map<String, Boolean> pauseFlags = new ConcurrentHashMap<>();
    private final Map<String, Boolean> cancelFlags = new ConcurrentHashMap<>();

    @Override
    public void save(TaskAggregate task) {
        if (task == null || task.getTaskId() == null) {
            throw new IllegalArgumentException("Task or TaskId cannot be null");
        }
        tasks.put(task.getTaskId(), task);
    }

    @Override
    public TaskAggregate get(String taskId) {
        return tasks.get(taskId);
    }

    @Override
    public TaskAggregate findByTenantId(String tenantId) {
        return tasks.values().stream()
            .filter(task -> tenantId.equals(task.getTenantId()))
            .findFirst()
            .orElse(null);
    }

    @Override
    public List<TaskAggregate> findByPlanId(String planId) {
        return tasks.values().stream()
            .filter(task -> planId.equals(task.getPlanId()))
            .collect(Collectors.toList());
    }

    @Override
    public void remove(String taskId) {
        tasks.remove(taskId);
        stages.remove(taskId);
        contexts.remove(taskId);
        executors.remove(taskId);
        pauseFlags.remove(taskId);
        cancelFlags.remove(taskId);
    }

    @Override
    public void saveStages(String taskId, List<TaskStage> stageList) {
        stages.put(taskId, stageList);
    }

    @Override
    public List<TaskStage> getStages(String taskId) {
        return stages.get(taskId);
    }

    @Override
    public void saveContext(String taskId, TaskRuntimeContext context) {
        contexts.put(taskId, context);
    }

    @Override
    public TaskRuntimeContext getContext(String taskId) {
        return contexts.get(taskId);
    }

    @Override
    public void saveExecutor(String taskId, TaskExecutor executor) {
        executors.put(taskId, executor);
    }

    @Override
    public TaskExecutor getExecutor(String taskId) {
        return executors.get(taskId);
    }

    @Override
    public void requestPause(String taskId) {
        pauseFlags.put(taskId, true);
    }

    @Override
    public void clearPause(String taskId) {
        pauseFlags.remove(taskId);
    }

    @Override
    public void requestCancel(String taskId) {
        cancelFlags.put(taskId, true);
    }

    @Override
    public boolean isPauseRequested(String taskId) {
        return Boolean.TRUE.equals(pauseFlags.get(taskId));
    }

    @Override
    public boolean isCancelRequested(String taskId) {
        return Boolean.TRUE.equals(cancelFlags.get(taskId));
    }
}

