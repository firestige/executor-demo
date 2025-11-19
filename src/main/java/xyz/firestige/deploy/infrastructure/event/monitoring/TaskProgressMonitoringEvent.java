package xyz.firestige.deploy.infrastructure.event.monitoring;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.TaskStatus;
import java.time.LocalDateTime;

/**
 * 任务进度监控事件（技术事件，非领域事件）
 * 
 * <p>特点：
 * <ul>
 *   <li>高频发布（每 10 秒）</li>
 *   <li>不改变领域状态</li>
 *   <li>仅用于监控面板、告警系统</li>
 * </ul>
 * 
 * <p>与领域事件的区别：
 * <ul>
 *   <li>领域事件：低频（每个 Stage 完成），由 TaskAggregate 产生，有业务意义</li>
 *   <li>监控事件：高频（定时轮询），由 HeartbeatScheduler 产生，纯技术性</li>
 * </ul>
 * 
 * @since RF-18: 事件驱动架构重构
 */
public class TaskProgressMonitoringEvent {
    
    private final TaskId taskId;
    private final int currentStageIndex;
    private final int totalStages;
    private final double percentage;
    private final TaskStatus currentStatus;
    private final LocalDateTime timestamp;
    
    public TaskProgressMonitoringEvent(
            TaskId taskId,
            int currentStageIndex,
            int totalStages,
            double percentage,
            TaskStatus currentStatus,
            LocalDateTime timestamp) {
        this.taskId = taskId;
        this.currentStageIndex = currentStageIndex;
        this.totalStages = totalStages;
        this.percentage = percentage;
        this.currentStatus = currentStatus;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }
    
    // Getters
    
    public TaskId getTaskId() {
        return taskId;
    }
    
    public int getCurrentStageIndex() {
        return currentStageIndex;
    }
    
    public int getTotalStages() {
        return totalStages;
    }
    
    public double getPercentage() {
        return percentage;
    }
    
    public TaskStatus getCurrentStatus() {
        return currentStatus;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("TaskProgressMonitoringEvent[taskId=%s, progress=%d/%d (%.1f%%), status=%s, timestamp=%s]",
                taskId, currentStageIndex, totalStages, percentage * 100, currentStatus, timestamp);
    }
}
