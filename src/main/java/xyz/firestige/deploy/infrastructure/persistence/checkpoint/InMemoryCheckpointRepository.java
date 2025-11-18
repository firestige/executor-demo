package xyz.firestige.deploy.infrastructure.persistence.checkpoint;

import xyz.firestige.deploy.domain.task.CheckpointRepository;
import xyz.firestige.deploy.domain.task.TaskCheckpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现，默认存储。
 */
public class InMemoryCheckpointRepository implements CheckpointRepository {

    private final Map<String, TaskCheckpoint> store = new ConcurrentHashMap<>();

    @Override
    public void put(String taskId, TaskCheckpoint checkpoint) {
        if (taskId != null && checkpoint != null) {
            store.put(taskId, checkpoint);
        }
    }

    @Override
    public TaskCheckpoint get(String taskId) {
        return taskId == null ? null : store.get(taskId);
    }

    @Override
    public void remove(String taskId) {
        if (taskId != null) store.remove(taskId);
    }
}

