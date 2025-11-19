package xyz.firestige.deploy.domain.task;

import xyz.firestige.deploy.domain.shared.vo.TaskId;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 可插拔检查点存储抽象。
 */
public interface CheckpointRepository {
    void put(TaskId taskId, TaskCheckpoint checkpoint);
    TaskCheckpoint get(TaskId taskId);
    void remove(TaskId taskId);

    default void putBatch(Map<TaskId, TaskCheckpoint> batch) {
        if (batch != null) {
            batch.forEach(this::put);
        }
    }

    default Map<TaskId, TaskCheckpoint> getBatch(List<TaskId> taskIds) {
        return taskIds.stream().collect(Collectors.toMap(id -> id, this::get));
    }
}

