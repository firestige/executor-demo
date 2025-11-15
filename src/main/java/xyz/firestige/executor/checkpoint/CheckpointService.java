package xyz.firestige.executor.checkpoint;

import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskCheckpoint;
import xyz.firestige.executor.domain.task.TaskContext;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 检查点服务：在 Stage 边界保存/恢复 Task 检查点。
 */
public class CheckpointService {

    private final CheckpointStore store;

    public CheckpointService(CheckpointStore store) {
        this.store = store;
    }

    public void saveCheckpoint(TaskAggregate task, List<String> completedStageNames, int lastCompletedIndex) {
        TaskCheckpoint cp = new TaskCheckpoint();
        cp.getCompletedStageNames().addAll(completedStageNames);
        cp.setLastCompletedStageIndex(lastCompletedIndex);
        cp.setTimestamp(LocalDateTime.now());
        task.setCheckpoint(cp);
        store.put(task.getTaskId(), cp);
    }

    public TaskCheckpoint loadCheckpoint(TaskAggregate task) {
        TaskCheckpoint cp = store.get(task.getTaskId());
        if (cp != null) {
            task.setCheckpoint(cp);
        }
        return cp;
    }

    public void clearCheckpoint(TaskAggregate task) {
        store.remove(task.getTaskId());
        task.setCheckpoint(null);
    }
}

