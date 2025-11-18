package xyz.firestige.executor.state.event.plan;

import xyz.firestige.executor.domain.plan.PlanStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 计划状态事件基类
 * RF-11: 所有 Plan 状态相关事件的基类
 */
public abstract class PlanStatusEvent {

    /**
     * 事件 ID
     */
    private String eventId;

    /**
     * 计划 ID
     */
    private String planId;

    /**
     * 计划状态
     */
    private PlanStatus status;

    /**
     * 事件时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 消息
     */
    private String message;

    public PlanStatusEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    public PlanStatusEvent(String planId, PlanStatus status) {
        this();
        this.planId = planId;
        this.status = status;
    }

    public PlanStatusEvent(String planId, PlanStatus status, String message) {
        this(planId, status);
        this.message = message;
    }

    // Getters and Setters

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
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

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "eventId='" + eventId + '\'' +
                ", planId='" + planId + '\'' +
                ", status=" + status +
                ", timestamp=" + timestamp +
                ", message='" + message + '\'' +
                '}';
    }
}
