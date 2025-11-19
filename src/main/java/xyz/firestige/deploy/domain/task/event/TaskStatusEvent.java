package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskStatus;

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
    private TaskId taskId;

    /**
     * 租户 ID
     */
    private TenantId tenantId;

    /**
     * 计划 ID
     */
    private PlanId planId;

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

    public TaskStatusEvent(TaskId taskId, TaskStatus status) {
        this();
        this.taskId = taskId;
        this.status = status;
    }

    public TaskStatusEvent(TaskId taskId, TenantId tenantId, PlanId planId, TaskStatus status) {
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

    public TaskId getTaskId() {
        return taskId;
    }

    public void setTaskId(TaskId taskId) {
        this.taskId = taskId;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public void setTenantId(TenantId tenantId) {
        this.tenantId = tenantId;
    }

    public PlanId getPlanId() {
        return planId;
    }

    public void setPlanId(PlanId planId) {
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
