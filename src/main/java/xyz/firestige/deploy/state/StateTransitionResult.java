package xyz.firestige.deploy.state;

import xyz.firestige.deploy.state.event.TaskStatusEvent;

/**
 * 状态转移结果
 */
public class StateTransitionResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 原状态
     */
    private TaskStatus oldStatus;

    /**
     * 新状态
     */
    private TaskStatus newStatus;

    /**
     * 状态转移成功时发布的事件
     */
    private TaskStatusEvent event;

    /**
     * 错误消息（转移失败时）
     */
    private String errorMessage;

    public StateTransitionResult() {
    }

    public StateTransitionResult(boolean success) {
        this.success = success;
    }

    /**
     * 创建成功结果
     */
    public static StateTransitionResult success(TaskStatus oldStatus, TaskStatus newStatus, TaskStatusEvent event) {
        StateTransitionResult result = new StateTransitionResult(true);
        result.setOldStatus(oldStatus);
        result.setNewStatus(newStatus);
        result.setEvent(event);
        return result;
    }

    /**
     * 创建失败结果
     */
    public static StateTransitionResult failure(TaskStatus oldStatus, TaskStatus newStatus, String errorMessage) {
        StateTransitionResult result = new StateTransitionResult(false);
        result.setOldStatus(oldStatus);
        result.setNewStatus(newStatus);
        result.setErrorMessage(errorMessage);
        return result;
    }

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public TaskStatus getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(TaskStatus oldStatus) {
        this.oldStatus = oldStatus;
    }

    public TaskStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(TaskStatus newStatus) {
        this.newStatus = newStatus;
    }

    public TaskStatusEvent getEvent() {
        return event;
    }

    public void setEvent(TaskStatusEvent event) {
        this.event = event;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "StateTransitionResult{" +
                "success=" + success +
                ", oldStatus=" + oldStatus +
                ", newStatus=" + newStatus +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}

