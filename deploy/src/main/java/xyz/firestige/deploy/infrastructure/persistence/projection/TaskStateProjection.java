package xyz.firestige.deploy.infrastructure.persistence.projection;

import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Task 状态投影（纯数据 DTO，无行为）
 * @since T-016 投影型持久化
 */
public class TaskStateProjection {
    private TaskId taskId;
    private TenantId tenantId;
    private PlanId planId;
    private TaskStatus status;
    private boolean pauseRequested;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> stageNames;              // 全量阶段名称（来自 TaskCreatedEvent）
    private int lastCompletedStageIndex;          // 从 Checkpoint 推导，-1 表示尚未完成任何阶段

    public TaskStateProjection() {}

    public TaskStateProjection(TaskId taskId,
                               TenantId tenantId,
                               PlanId planId,
                               TaskStatus status,
                               List<String> stageNames) {
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.planId = planId;
        this.status = status;
        this.pauseRequested = false;
        this.stageNames = stageNames != null ? List.copyOf(stageNames) : List.of();
        this.lastCompletedStageIndex = -1;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private TaskId taskId;
        private TenantId tenantId;
        private PlanId planId;
        private TaskStatus status;
        private boolean pauseRequested;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<String> stageNames;
        private int lastCompletedStageIndex = -1;
        public Builder taskId(TaskId v){ this.taskId=v; return this; }
        public Builder tenantId(TenantId v){ this.tenantId=v; return this; }
        public Builder planId(PlanId v){ this.planId=v; return this; }
        public Builder status(TaskStatus v){ this.status=v; return this; }
        public Builder pauseRequested(boolean v){ this.pauseRequested=v; return this; }
        public Builder createdAt(LocalDateTime v){ this.createdAt=v; return this; }
        public Builder updatedAt(LocalDateTime v){ this.updatedAt=v; return this; }
        public Builder stageNames(List<String> v){ this.stageNames=v; return this; }
        public Builder lastCompletedStageIndex(int v){ this.lastCompletedStageIndex=v; return this; }
        public TaskStateProjection build(){
            TaskStateProjection p = new TaskStateProjection();
            p.taskId = taskId;
            p.tenantId = tenantId;
            p.planId = planId;
            p.status = status;
            p.pauseRequested = pauseRequested;
            p.stageNames = stageNames != null ? List.copyOf(stageNames) : List.of();
            p.lastCompletedStageIndex = lastCompletedStageIndex;
            p.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
            p.updatedAt = updatedAt != null ? updatedAt : p.createdAt;
            return p;
        }
    }

    public TaskId getTaskId() { return taskId; }
    public void setTaskId(TaskId taskId) { this.taskId = taskId; }
    public TenantId getTenantId() { return tenantId; }
    public void setTenantId(TenantId tenantId) { this.tenantId = tenantId; }
    public PlanId getPlanId() { return planId; }
    public void setPlanId(PlanId planId) { this.planId = planId; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public boolean isPauseRequested() { return pauseRequested; }
    public void setPauseRequested(boolean pauseRequested) { this.pauseRequested = pauseRequested; this.updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<String> getStageNames() { return stageNames; }
    public void setStageNames(List<String> stageNames) { this.stageNames = stageNames != null ? List.copyOf(stageNames) : List.of(); this.updatedAt = LocalDateTime.now(); }
    public int getLastCompletedStageIndex() { return lastCompletedStageIndex; }
    public void setLastCompletedStageIndex(int lastCompletedStageIndex) { this.lastCompletedStageIndex = lastCompletedStageIndex; this.updatedAt = LocalDateTime.now(); }

    @Override
    public String toString() {
        return "TaskStateProjection{" +
                "taskId=" + taskId +
                ", tenantId=" + tenantId +
                ", planId=" + planId +
                ", status=" + status +
                ", pauseRequested=" + pauseRequested +
                ", lastCompletedStageIndex=" + lastCompletedStageIndex +
                ", stageCount=" + stageNames.size() +
                '}';
    }
}
