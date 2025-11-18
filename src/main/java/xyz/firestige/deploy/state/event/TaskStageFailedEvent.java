package xyz.firestige.deploy.state.event;

import xyz.firestige.deploy.exception.FailureInfo;
import xyz.firestige.deploy.state.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 失败事件
 */
public class TaskStageFailedEvent extends TaskStatusEvent {

    /**
     * Stage 名称
     */
    private String stageName;

    /**
     * 受影响的租户列表
     */
    private List<String> affectedTenants;

    public TaskStageFailedEvent() {
        super();
        setStatus(TaskStatus.RUNNING);
        this.affectedTenants = new ArrayList<>();
    }

    public TaskStageFailedEvent(String taskId, String stageName, FailureInfo failureInfo) {
        super(taskId, TaskStatus.RUNNING);
        this.stageName = stageName;
        setFailureInfo(failureInfo);
        this.affectedTenants = new ArrayList<>();
        setMessage("Stage 执行失败: " + stageName);
    }

    public TaskStageFailedEvent(String taskId, String stageName, FailureInfo failureInfo, List<String> affectedTenants) {
        super(taskId, TaskStatus.RUNNING);
        this.stageName = stageName;
        setFailureInfo(failureInfo);
        this.affectedTenants = affectedTenants != null ? affectedTenants : new ArrayList<>();
        setMessage("Stage 执行失败: " + stageName + ", 受影响租户数: " + this.affectedTenants.size());
    }

    // Getters and Setters

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public List<String> getAffectedTenants() {
        return affectedTenants;
    }

    public void setAffectedTenants(List<String> affectedTenants) {
        this.affectedTenants = affectedTenants;
    }
}

