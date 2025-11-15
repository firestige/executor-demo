package xyz.firestige.executor.state.event;

import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.state.TaskStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 任务状态事件基类
 * 所有任务状态相关事件的基类
 */
public abstract class TaskStatusEvent {

    /**
     * 事件 ID
     */
    private String eventId;

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 租户 ID
     */
    private String tenantId;

    /**
     * 计划 ID
     */
    private Long planId;

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 事件时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 消息
     */
    private String message;

    /**
     * 失败信息（可选）
     */
    private FailureInfo failureInfo;

    /**
     * 自增序列号
     */
    private long sequenceId;

    public TaskStatusEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    public TaskStatusEvent(String taskId, TaskStatus status) {
        this();
        this.taskId = taskId;
        this.status = status;
    }

    public TaskStatusEvent(String taskId, String tenantId, Long planId, TaskStatus status) {
        this();
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.planId = planId;
        this.status = status;
    }

    // Getters and Setters

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }

    public void setFailureInfo(FailureInfo failureInfo) {
        this.failureInfo = failureInfo;
    }

    public long getSequenceId() {
        return sequenceId;
    }

    public void setSequenceId(long sequenceId) {
        this.sequenceId = sequenceId;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "eventId='" + eventId + '\'' +
                ", taskId='" + taskId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", status=" + status +
                ", timestamp=" + timestamp +
                '}';
    }
}
