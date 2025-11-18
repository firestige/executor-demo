package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * 任务重试开始事件
 * 
 * <p>携带信息：
 * <ul>
 *   <li>是否从检查点恢复</li>
 *   <li>当前进度（仅在从检查点恢复时有效）</li>
 * </ul>
 */
public class TaskRetryStartedEvent extends TaskStatusEvent {
    private final boolean fromCheckpoint;
    private final int currentStageIndex;
    private final int totalStages;
    
    /**
     * 基础构造函数（不带进度信息）
     */
    public TaskRetryStartedEvent(String taskId, boolean fromCheckpoint) {
        super(taskId, TaskStatus.RUNNING);
        this.fromCheckpoint = fromCheckpoint;
        this.currentStageIndex = 0;
        this.totalStages = 0;
        setMessage(fromCheckpoint ? "从检查点重试任务" : "从头重试任务");
    }
    
    /**
     * 完整构造函数（带进度信息，用于检查点恢复）
     */
    public TaskRetryStartedEvent(String taskId, boolean fromCheckpoint, int currentStageIndex, int totalStages) {
        super(taskId, TaskStatus.RUNNING);
        this.fromCheckpoint = fromCheckpoint;
        this.currentStageIndex = currentStageIndex;
        this.totalStages = totalStages;
        setMessage(String.format("从检查点重试任务（进度: %d/%d）", currentStageIndex, totalStages));
    }
    
    public boolean isFromCheckpoint() { 
        return fromCheckpoint; 
    }
    
    public int getCurrentStageIndex() {
        return currentStageIndex;
    }
    
    public int getTotalStages() {
        return totalStages;
    }
    
    /**
     * 计算完成百分比
     */
    public double getPercentage() {
        return totalStages == 0 ? 0 : (currentStageIndex * 100.0 / totalStages);
    }
}
