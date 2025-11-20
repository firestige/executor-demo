package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.event.DomainEvent;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 任务状态事件基类
 * 所有任务状态相关事件的基类
 */
public abstract class TaskStatusEvent extends DomainEvent {

    private final TaskInfo info;

    public TaskStatusEvent(TaskInfo info) {
        super();
        this.info = info;
    }

    public TaskStatusEvent(TaskInfo info, String message) {
        this(info);
        setMessage(message);
    }

    // Getters and Setters

    public TaskId getTaskId() {
        return info.getTaskId();
    }

    public String getTaskIdAsString() {
        return info.getTaskId().toString();
    }

    public TenantId getTenantId() {
        return info.getTenantId();
    }

    public  String getTenantIdAsString() {
        return info.getTenantId().toString();
    }

    public PlanId getPlanId() {
        return info.getPlanId();
    }

    public  String getPlanIdAsString() {
        return info.getPlanId().toString();
    }

    public TaskStatus getStatus() {
        return info.getStatus();
    }

    public String getStatusAsString() {
        return info.getStatus().toString();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "eventId='" + this.getEventId() + '\'' +
                ", taskId='" + this.getTaskIdAsString() + '\'' +
                ", tenantId='" + this.getTenantIdAsString() + '\'' +
                ", planId='" + this.getPlanIdAsString() + '\'' +
                ", status=" + this.getStatusAsString() +
                ", timestamp=" + this.getFormattedTimestamp() +
                '}';
    }
}
