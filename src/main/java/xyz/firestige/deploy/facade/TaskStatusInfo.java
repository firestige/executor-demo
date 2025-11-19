package xyz.firestige.deploy.facade;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * 任务状态信息（Facade 层 DTO）
 * 用于查询任务状态的返回值
 */
public class TaskStatusInfo {

    private TaskId taskId;
    private TaskStatus status;
    private String message;
    private Integer currentStage;
    private Integer totalStages;
    private Long startedAt;
    private Long endedAt;

    public TaskStatusInfo() {
    }

    public TaskStatusInfo(TaskId taskId, TaskStatus status) {
        this.taskId = taskId;
        this.status = status;
    }

    /**
     * 创建失败的状态信息（任务不存在）
     */
    public static TaskStatusInfo failure(String message) {
        TaskStatusInfo info = new TaskStatusInfo();
        info.setStatus(null); // status 为 null 表示任务不存在
        info.setMessage(message);
        return info;
    }

    /**
     * 创建成功的状态信息
     */
    public static TaskStatusInfo success(TaskId taskId, TaskStatus status, String message) {
        TaskStatusInfo info = new TaskStatusInfo();
        info.setTaskId(taskId);
        info.setStatus(status);
        info.setMessage(message);
        return info;
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public void setTaskId(TaskId taskId) {
        this.taskId = taskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(Integer currentStage) {
        this.currentStage = currentStage;
    }

    public Integer getTotalStages() {
        return totalStages;
    }

    public void setTotalStages(Integer totalStages) {
        this.totalStages = totalStages;
    }

    public Long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Long startedAt) {
        this.startedAt = startedAt;
    }

    public Long getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Long endedAt) {
        this.endedAt = endedAt;
    }
}
