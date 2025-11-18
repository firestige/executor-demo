package xyz.firestige.deploy.checkpoint;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskCheckpoint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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

    /**
     * 批量恢复检查点（CP-03）：返回 taskId -> TaskCheckpoint 的映射，不修改传入聚合对象。
     */
    public Map<String, TaskCheckpoint> loadMultiple(List<String> taskIds) {
        Map<String, TaskCheckpoint> result = new HashMap<>();
        if (taskIds == null || taskIds.isEmpty()) return result;
        for (String id : taskIds) {
            TaskCheckpoint cp = store.get(id);
            if (cp != null) result.put(id, cp);
        }
        return result;
    }
}
