package xyz.firestige.executor.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务上下文
 * 包含任务执行过程中的所有上下文信息、检查点历史和执行状态
 */
public class TaskContext {
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 检查点历史列表（按时间顺序）
     */
    private List<Checkpoint> checkpointHistory;
    
    /**
     * 当前激活的检查点
     */
    private Checkpoint currentCheckpoint;
    
    /**
     * 执行上下文数据 - 存储任务级别的共享数据
     */
    private Map<String, Object> executionContext;
    
    /**
     * 控制信号 - 用于暂停、停止等控制
     */
    private volatile ControlSignal controlSignal;
    
    /**
     * 错误信息（如果有）
     */
    private String errorMessage;
    
    /**
     * 异常堆栈（如果有）
     */
    private String errorStackTrace;
    
    /**
     * 上下文创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;
    
    public TaskContext() {
        this.checkpointHistory = new ArrayList<>();
        this.executionContext = new HashMap<>();
        this.controlSignal = ControlSignal.NONE;
        this.createTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public TaskContext(String taskId) {
        this();
        this.taskId = taskId;
    }
    
    /**
     * 添加检查点到历史
     */
    public void addCheckpoint(Checkpoint checkpoint) {
        this.checkpointHistory.add(checkpoint);
        this.currentCheckpoint = checkpoint;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 获取最新的检查点
     */
    public Checkpoint getLatestCheckpoint() {
        if (currentCheckpoint != null) {
            return currentCheckpoint;
        }
        if (!checkpointHistory.isEmpty()) {
            return checkpointHistory.get(checkpointHistory.size() - 1);
        }
        return null;
    }
    
    /**
     * 检查是否有暂停信号
     */
    public boolean isPauseRequested() {
        return controlSignal == ControlSignal.PAUSE;
    }
    
    /**
     * 检查是否有停止信号
     */
    public boolean isStopRequested() {
        return controlSignal == ControlSignal.STOP;
    }
    
    /**
     * 重置控制信号
     */
    public void resetControlSignal() {
        this.controlSignal = ControlSignal.NONE;
    }
    
    // Getters and Setters
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public List<Checkpoint> getCheckpointHistory() {
        return checkpointHistory;
    }
    
    public void setCheckpointHistory(List<Checkpoint> checkpointHistory) {
        this.checkpointHistory = checkpointHistory;
    }
    
    public Checkpoint getCurrentCheckpoint() {
        return currentCheckpoint;
    }
    
    public void setCurrentCheckpoint(Checkpoint currentCheckpoint) {
        this.currentCheckpoint = currentCheckpoint;
    }
    
    public Map<String, Object> getExecutionContext() {
        return executionContext;
    }
    
    public void setExecutionContext(Map<String, Object> executionContext) {
        this.executionContext = executionContext;
    }
    
    public void putExecutionContext(String key, Object value) {
        this.executionContext.put(key, value);
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public Object getExecutionContext(String key) {
        return this.executionContext.get(key);
    }
    
    public ControlSignal getControlSignal() {
        return controlSignal;
    }
    
    public void setControlSignal(ControlSignal controlSignal) {
        this.controlSignal = controlSignal;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public String getErrorStackTrace() {
        return errorStackTrace;
    }
    
    public void setErrorStackTrace(String errorStackTrace) {
        this.errorStackTrace = errorStackTrace;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public LocalDateTime getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    public void setLastUpdateTime(LocalDateTime lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
    
    /**
     * 控制信号枚举
     */
    public enum ControlSignal {
        NONE,    // 无信号
        PAUSE,   // 暂停信号
        STOP     // 停止信号
    }
}
