package xyz.firestige.deploy.domain.task;

import xyz.firestige.deploy.exception.FailureInfo;
import xyz.firestige.deploy.state.TaskStatus;

/**
 * Task 操作结果
 * 用于单个 Task 级别的操作（暂停、恢复、回滚、重试、取消）
 * 明确区分 Task 聚合的操作结果
 *
 * 注意：这是重构后的版本，与 facade 包下的旧版本不同
 * 旧版本（facade/TaskOperationResult）将在 Phase 5 删除
 */
public class TaskOperationResult {

    private boolean success;
    private String taskId;
    private TaskStatus status;
    private FailureInfo failureInfo;
    private String message;

    public TaskOperationResult() {
    }

    // 静态工厂方法

    /**
     * 创建成功结果
     */
    public static TaskOperationResult success(String taskId, TaskStatus status, String message) {
        TaskOperationResult result = new TaskOperationResult();
        result.success = true;
        result.taskId = taskId;
        result.status = status;
        result.message = message;
        return result;
    }

    /**
     * 创建失败结果
     */
    public static TaskOperationResult failure(String taskId, FailureInfo failureInfo, String message) {
        TaskOperationResult result = new TaskOperationResult();
        result.success = false;
        result.taskId = taskId;
        result.failureInfo = failureInfo;
        result.message = message;
        return result;
    }

    /**
     * 创建失败结果（简化版）
     */
    public static TaskOperationResult failure(String message) {
        TaskOperationResult result = new TaskOperationResult();
        result.success = false;
        result.message = message;
        return result;
    }

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }

    public void setFailureInfo(FailureInfo failureInfo) {
        this.failureInfo = failureInfo;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "TaskOperationResult{" +
                "success=" + success +
                ", taskId='" + taskId + '\'' +
                ", status=" + status +
                ", message='" + message + '\'' +
                '}';
    }
}


