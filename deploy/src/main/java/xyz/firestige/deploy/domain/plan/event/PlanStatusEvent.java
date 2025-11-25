package xyz.firestige.deploy.domain.plan.event;

import xyz.firestige.deploy.domain.plan.PlanInfo;
import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.event.DomainEvent;
import xyz.firestige.deploy.domain.shared.vo.PlanId;

/**
 * 计划状态事件基类
 * RF-11: 所有 Plan 状态相关事件的基类
 */
public abstract class PlanStatusEvent extends DomainEvent {

    private final PlanInfo info;

    public PlanStatusEvent(PlanInfo info) {
        this(info, "");
    }

    public PlanStatusEvent(PlanInfo info, String message) {
        super();
        this.info = info;
        setMessage(message);
    }

    // Getters and Setters

    public PlanId getPlanId() {
        return info.getPlanId();
    }

    public String getPlanIdAsString() {
        return info.getPlanId().getValue();
    }

    public PlanStatus getStatus() {
        return info.getStatus();
    }

    public String getStatusAsString() {
        return info.getStatus().name();
    }

    @Override
    public String toString() {
        return this.getEventName() + "{" +
                "eventId='" + this.getEventId() + '\'' +
                ", planId='" + this.getPlanIdAsString() + '\'' +
                ", status=" + this.getStatusAsString() +
                ", timestamp=" + this.getFormattedTimestamp() +
                ", message='" + this.getMessage() + '\'' +
                '}';
    }
}
