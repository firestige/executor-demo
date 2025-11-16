package xyz.firestige.executor.checkpoint;

import xyz.firestige.executor.domain.task.TaskCheckpoint;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 可插拔检查点存储抽象。
 */
public interface CheckpointStore {
    void put(String taskId, TaskCheckpoint checkpoint);
    TaskCheckpoint get(String taskId);
    void remove(String taskId);

    default void putBatch(Map<String, TaskCheckpoint> batch) {
        if (batch != null) {
            batch.forEach(this::put);
        }
    }

    default Map<String, TaskCheckpoint> getBatch(List<String> taskIds) {
        return taskIds.stream().collect(Collectors.toMap(id -> id, this::get));
    }
}

