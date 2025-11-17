package xyz.firestige.executor.application.dto;

import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.state.TaskStatus;

/**
 * Task 信息
 * 值对象，表达 Task 实体的基本信息
 * 不可变对象
 */
public class TaskInfo {

    private final String taskId;
    private final String tenantId;
    private final Long deployUnitVersion;
    private final TaskStatus status;

    public TaskInfo(String taskId, String tenantId, Long deployUnitVersion, TaskStatus status) {
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.deployUnitVersion = deployUnitVersion;
        this.status = status;
    }

    /**
     * 静态工厂方法：从领域模型构造
     */
    public static TaskInfo from(TaskAggregate task) {
        return new TaskInfo(
            task.getTaskId(),
            task.getTenantId(),
            task.getDeployUnitVersion(),
            task.getStatus()
        );
    }

    // Getters only (值对象不可变，无 Setters)

    public String getTaskId() {
        return taskId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Long getDeployUnitVersion() {
        return deployUnitVersion;
    }

    public TaskStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "TaskInfo{" +
                "taskId='" + taskId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", deployUnitVersion=" + deployUnitVersion +
                ", status=" + status +
                '}';
    }
}

