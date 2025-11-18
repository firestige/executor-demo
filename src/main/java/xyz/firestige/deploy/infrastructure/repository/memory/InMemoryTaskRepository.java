package xyz.firestige.deploy.infrastructure.repository.memory;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Task Repository 内存实现（DDD 重构：简化方案）
 *
 * 使用 ConcurrentHashMap 存储 Task 聚合根
 *
 * 注意：
 * - 只管理聚合根，不管理运行时状态
 * - 运行时状态由 TaskRuntimeRepository 管理
 * - 生产环境应替换为 Redis 或数据库实现
 *
 * @since DDD 重构 Phase 18 - RF-09 简化方案
 */
public class InMemoryTaskRepository implements TaskRepository {

    private final Map<String, TaskAggregate> tasks = new ConcurrentHashMap<>();

    @Override
    public void save(TaskAggregate task) {
        if (task == null || task.getTaskId() == null) {
            throw new IllegalArgumentException("Task or TaskId cannot be null");
        }
        tasks.put(task.getTaskId(), task);
    }

    @Override
    public void remove(String taskId) {
        tasks.remove(taskId);
    }

    @Override
    public Optional<TaskAggregate> findById(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public Optional<TaskAggregate> findByTenantId(String tenantId) {
        return tasks.values().stream()
            .filter(task -> tenantId.equals(task.getTenantId()))
            .findFirst();
    }

    @Override
    public List<TaskAggregate> findByPlanId(String planId) {
        return tasks.values().stream()
            .filter(task -> planId.equals(task.getPlanId()))
            .collect(Collectors.toList());
    }
}

