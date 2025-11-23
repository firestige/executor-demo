package xyz.firestige.deploy.infrastructure.persistence.projection;

import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Plan 状态投影（纯数据 DTO，无行为）
 * <p>
 * 用途：
 * - 持久化 Plan 核心状态供查询
 * - 重启后重建 Plan-Task 关联关系
 * - 不参与业务不变式，仅供查询使用
 *
 * @since T-016 投影型持久化
 */
public class PlanStateProjection {
    private PlanId planId;
    private PlanStatus status;
    private List<TaskId> taskIds;
    private int maxConcurrency;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;

    // ========== Constructors ==========

    public PlanStateProjection() {
        this.taskIds = new ArrayList<>();
    }

    public PlanStateProjection(PlanId planId, PlanStatus status, int maxConcurrency) {
        this.planId = planId;
        this.status = status;
        this.maxConcurrency = maxConcurrency;
        this.taskIds = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Builder Pattern ==========

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private PlanId planId;
        private PlanStatus status;
        private List<TaskId> taskIds = new ArrayList<>();
        private int maxConcurrency;
        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime updatedAt;

        public Builder planId(PlanId planId) {
            this.planId = planId;
            return this;
        }

        public Builder status(PlanStatus status) {
            this.status = status;
            return this;
        }

        public Builder taskIds(List<TaskId> taskIds) {
            this.taskIds = taskIds != null ? new ArrayList<>(taskIds) : new ArrayList<>();
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder startedAt(LocalDateTime startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public PlanStateProjection build() {
            PlanStateProjection projection = new PlanStateProjection();
            projection.planId = this.planId;
            projection.status = this.status;
            projection.taskIds = this.taskIds;
            projection.maxConcurrency = this.maxConcurrency;
            projection.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            projection.startedAt = this.startedAt;
            projection.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return projection;
        }
    }

    // ========== Getters / Setters ==========

    public PlanId getPlanId() {
        return planId;
    }

    public void setPlanId(PlanId planId) {
        this.planId = planId;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public List<TaskId> getTaskIds() {
        return taskIds;
    }

    public void setTaskIds(List<TaskId> taskIds) {
        this.taskIds = taskIds != null ? taskIds : new ArrayList<>();
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ========== Object Methods ==========

    @Override
    public String toString() {
        return "PlanStateProjection{" +
                "planId=" + planId +
                ", status=" + status +
                ", taskIds=" + taskIds +
                ", maxConcurrency=" + maxConcurrency +
                ", createdAt=" + createdAt +
                ", startedAt=" + startedAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

