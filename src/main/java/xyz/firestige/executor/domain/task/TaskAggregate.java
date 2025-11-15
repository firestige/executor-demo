package xyz.firestige.executor.domain.task;

import xyz.firestige.executor.execution.StageResult;
import xyz.firestige.executor.state.TaskStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 单租户 Task 聚合
 */
public class TaskAggregate {

    private String taskId;
    private String planId;
    private String tenantId;

    private Long deployUnitId;
    private Long deployUnitVersion;
    private String deployUnitName;

    private TaskStatus status;
    private int currentStageIndex;
    private int retryCount;
    private Integer maxRetry; // null 表示使用全局

    private TaskCheckpoint checkpoint;
    private List<StageResult> stageResults = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public TaskAggregate(String taskId, String planId, String tenantId) {
        this.taskId = taskId;
        this.planId = planId;
        this.tenantId = tenantId;
        this.status = TaskStatus.CREATED;
    }

    public String getTaskId() { return taskId; }
    public String getPlanId() { return planId; }
    public String getTenantId() { return tenantId; }

    public Long getDeployUnitId() { return deployUnitId; }
    public void setDeployUnitId(Long deployUnitId) { this.deployUnitId = deployUnitId; }
    public Long getDeployUnitVersion() { return deployUnitVersion; }
    public void setDeployUnitVersion(Long deployUnitVersion) { this.deployUnitVersion = deployUnitVersion; }
    public String getDeployUnitName() { return deployUnitName; }
    public void setDeployUnitName(String deployUnitName) { this.deployUnitName = deployUnitName; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public int getCurrentStageIndex() { return currentStageIndex; }
    public void setCurrentStageIndex(int currentStageIndex) { this.currentStageIndex = currentStageIndex; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public Integer getMaxRetry() { return maxRetry; }
    public void setMaxRetry(Integer maxRetry) { this.maxRetry = maxRetry; }

    public TaskCheckpoint getCheckpoint() { return checkpoint; }
    public void setCheckpoint(TaskCheckpoint checkpoint) { this.checkpoint = checkpoint; }

    public List<StageResult> getStageResults() { return stageResults; }
    public void setStageResults(List<StageResult> stageResults) { this.stageResults = stageResults; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}

