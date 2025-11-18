package xyz.firestige.deploy.infrastructure.state;

import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;

import java.time.LocalDateTime;

/**
 * 状态转移记录
 * 记录任务状态的每次转移
 */
public class StateTransition {

    /**
     * 源状态
     */
    private TaskStatus fromStatus;

    /**
     * 目标状态
     */
    private TaskStatus toStatus;

    /**
     * 转移时间
     */
    private LocalDateTime timestamp;

    /**
     * 转移原因
     */
    private String reason;

    /**
     * 失败信息（如果相关）
     */
    private FailureInfo failureInfo;

    public StateTransition() {
        this.timestamp = LocalDateTime.now();
    }

    public StateTransition(TaskStatus fromStatus, TaskStatus toStatus) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.timestamp = LocalDateTime.now();
    }

    public StateTransition(TaskStatus fromStatus, TaskStatus toStatus, String reason) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters

    public TaskStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(TaskStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public TaskStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(TaskStatus toStatus) {
        this.toStatus = toStatus;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }

    public void setFailureInfo(FailureInfo failureInfo) {
        this.failureInfo = failureInfo;
    }

    @Override
    public String toString() {
        return "StateTransition{" +
                "fromStatus=" + fromStatus +
                ", toStatus=" + toStatus +
                ", timestamp=" + timestamp +
                ", reason='" + reason + '\'' +
                '}';
    }
}

