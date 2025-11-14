package xyz.firestige.executor.execution.checkpoint;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 检查点数据类
 * 用于保存任务执行的中间状态，支持故障恢复
 */
public class Checkpoint {

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 当前执行到的 Stage 名称
     */
    private String stageName;

    /**
     * 每个 Stage 的输出数据
     * Key: Stage 名称, Value: Stage 输出数据
     */
    private Map<String, Map<String, Object>> stageData;

    /**
     * 检查点创建时间
     */
    private LocalDateTime timestamp;

    public Checkpoint() {
        this.stageData = new HashMap<>();
        this.timestamp = LocalDateTime.now();
    }

    public Checkpoint(String taskId, String stageName) {
        this.taskId = taskId;
        this.stageName = stageName;
        this.stageData = new HashMap<>();
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 保存 Stage 的输出数据
     */
    public void saveStageData(String stageName, Map<String, Object> data) {
        this.stageData.put(stageName, data);
    }

    /**
     * 获取 Stage 的输出数据
     */
    public Map<String, Object> getStageData(String stageName) {
        return this.stageData.get(stageName);
    }

    /**
     * 是否包含某个 Stage 的数据
     */
    public boolean hasStageData(String stageName) {
        return this.stageData.containsKey(stageName);
    }

    // Getters and Setters

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public Map<String, Map<String, Object>> getStageData() {
        return stageData;
    }

    public void setStageData(Map<String, Map<String, Object>> stageData) {
        this.stageData = stageData;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Checkpoint{" +
                "taskId='" + taskId + '\'' +
                ", stageName='" + stageName + '\'' +
                ", stageDataCount=" + stageData.size() +
                ", timestamp=" + timestamp +
                '}';
    }
}

