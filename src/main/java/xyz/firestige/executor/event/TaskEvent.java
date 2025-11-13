package xyz.firestige.executor.event;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务事件基类
 * 所有任务相关事件的基础类，使用灵活的上下文存储事件数据
 */
public abstract class TaskEvent {
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 事件类型（BEFORE/AFTER）
     */
    private TaskEventType eventType;
    
    /**
     * 事件上下文 - 存储事件相关的任意数据
     * 可以包含：状态、租户信息、服务信息、错误信息等
     */
    private Map<String, Object> context;
    
    /**
     * 事件时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 事件来源
     */
    private String source;
    
    public TaskEvent() {
        this.context = new HashMap<>();
        this.timestamp = LocalDateTime.now();
    }
    
    public TaskEvent(String taskId, TaskEventType eventType) {
        this();
        this.taskId = taskId;
        this.eventType = eventType;
    }
    
    /**
     * 获取事件名称（由子类实现）
     */
    public abstract String getEventName();
    
    /**
     * 添加上下文数据
     */
    public void putContext(String key, Object value) {
        this.context.put(key, value);
    }
    
    /**
     * 获取上下文数据
     */
    public Object getContext(String key) {
        return this.context.get(key);
    }
    
    /**
     * 判断是否为事前事件
     */
    public boolean isBeforeEvent() {
        return eventType == TaskEventType.BEFORE;
    }
    
    /**
     * 判断是否为事后事件
     */
    public boolean isAfterEvent() {
        return eventType == TaskEventType.AFTER;
    }
    
    // Getters and Setters
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public TaskEventType getEventType() {
        return eventType;
    }
    
    public void setEventType(TaskEventType eventType) {
        this.eventType = eventType;
    }
    
    public Map<String, Object> getContext() {
        return context;
    }
    
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    @Override
    public String toString() {
        return getEventName() + "{" +
                "taskId='" + taskId + '\'' +
                ", eventType=" + eventType +
                ", timestamp=" + timestamp +
                ", source='" + source + '\'' +
                '}';
    }
}
