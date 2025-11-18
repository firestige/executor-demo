package xyz.firestige.deploy.domain.task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task 检查点
 */
public class TaskCheckpoint {
    private int lastCompletedStageIndex;
    private final List<String> completedStageNames = new ArrayList<>();
    private final Map<String, Object> customData = new ConcurrentHashMap<>();
    private LocalDateTime timestamp = LocalDateTime.now();

    public int getLastCompletedStageIndex() {
        return lastCompletedStageIndex;
    }

    public void setLastCompletedStageIndex(int lastCompletedStageIndex) {
        this.lastCompletedStageIndex = lastCompletedStageIndex;
    }

    public List<String> getCompletedStageNames() {
        return completedStageNames;
    }

    public Map<String, Object> getCustomData() {
        return customData;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

