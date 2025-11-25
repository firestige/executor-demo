package xyz.firestige.deploy.domain.task;

import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

/**
 * Task 信息
 * 值对象，表达 Task 实体的基本信息
 * 不可变对象
 */
public class TaskInfo {

    private final TaskId taskId;
    private final TenantId tenantId;
    private final PlanId planId;
    private final String deployUnitName;
    private final Long deployUnitVersion;
    private final TaskStatus status;

    public TaskInfo(TaskId taskId, TenantId tenantId, PlanId planId, String deployUnitName, Long deployUnitVersion, TaskStatus status) {
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.planId = planId;
        this.deployUnitName = deployUnitName;
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
            task.getPlanId(),
            task.getDeployUnitName(),
            task.getDeployUnitVersion(),
            task.getStatus()
        );
    }

    // Getters only (值对象不可变，无 Setters)

    public TaskId getTaskId() {
        return taskId;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public PlanId getPlanId() {
        return planId;
    }

    public String getDeployUnitName() {
        return deployUnitName;
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


