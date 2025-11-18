package xyz.firestige.deploy.domain.task.event;

import java.time.LocalDateTime;

/**
 * Task 领域事件基类
 * <p>
 * 职责：
 * - 提供事件的公共属性（taskId, occurredOn）
 * - 统一事件接口
 */
public abstract class TaskEvent {
    
    protected final String taskId;
    protected final LocalDateTime occurredOn;
    
    protected TaskEvent(String taskId, LocalDateTime occurredOn) {
        this.taskId = taskId;
        this.occurredOn = occurredOn != null ? occurredOn : LocalDateTime.now();
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public LocalDateTime getOccurredOn() {
        return occurredOn;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{taskId='" + taskId + "', occurredOn=" + occurredOn + "}";
    }
}
