package xyz.firestige.executor.state.event;

import xyz.firestige.executor.state.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务回滚完成事件
 */
public class TaskRolledBackEvent extends TaskStatusEvent {

    /**
     * 已回滚的 Stage 列表
     */
    private List<String> rolledBackStages;
    // Snapshot info of previous good config
    private Long prevDeployUnitId;
    private Long prevDeployUnitVersion;
    private String prevDeployUnitName;

    public TaskRolledBackEvent() {
        super();
        setStatus(TaskStatus.ROLLED_BACK);
        this.rolledBackStages = new ArrayList<>();
    }

    public TaskRolledBackEvent(String taskId, List<String> rolledBackStages) {
        super(taskId, TaskStatus.ROLLED_BACK);
        this.rolledBackStages = rolledBackStages != null ? rolledBackStages : new ArrayList<>();
        setMessage("任务回滚完成，回滚 Stage 数: " + this.rolledBackStages.size());
    }

    // Getters and Setters

    public List<String> getRolledBackStages() {
        return rolledBackStages;
    }

    public void setRolledBackStages(List<String> rolledBackStages) {
        this.rolledBackStages = rolledBackStages;
    }

    public Long getPrevDeployUnitId() {
        return prevDeployUnitId;
    }

    public void setPrevDeployUnitId(Long prevDeployUnitId) {
        this.prevDeployUnitId = prevDeployUnitId;
    }

    public Long getPrevDeployUnitVersion() {
        return prevDeployUnitVersion;
    }

    public void setPrevDeployUnitVersion(Long prevDeployUnitVersion) {
        this.prevDeployUnitVersion = prevDeployUnitVersion;
    }

    public String getPrevDeployUnitName() {
        return prevDeployUnitName;
    }

    public void setPrevDeployUnitName(String prevDeployUnitName) {
        this.prevDeployUnitName = prevDeployUnitName;
    }
}
