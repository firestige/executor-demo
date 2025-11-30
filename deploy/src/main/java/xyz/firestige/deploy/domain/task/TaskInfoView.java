package xyz.firestige.deploy.domain.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

import java.time.LocalDateTime;

/**
 * Task 信息视图（宽表设计）
 * <p>
 * 职责：
 * 1. 承载 Task 的完整基本信息（宽表）
 * 2. 为领域事件提供统一的信息视图
 * 3. 支持 JSON 稀疏表序列化（忽略 null 字段）
 * <p>
 * 设计理念：
 * - 宽表设计：包含所有可能需要的字段
 * - 稀疏表优化：序列化时自动忽略 null 字段
 * - 向后兼容：可转换为 TaskInfo
 * - 静态工厂方法：事件调用 from(task) 创建
 * <p>
 * 使用场景：
 * - 领域事件负载（事件自己调用 TaskInfoView.from(task)）
 * - 监控系统查询
 * - 日志记录
 *
 * @since T-035 统一事件负载模型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)  // 忽略 null 字段
public class TaskInfoView {

    // ============================================
    // 核心标识
    // ============================================

    private final String taskId;
    private final String tenantId;
    private final String planId;

    // ============================================
    // 部署信息
    // ============================================

    private final String deployUnitName;
    private final String deployUnitId;
    private final Long deployUnitVersion;
    private final TaskStatus status;

    // ============================================
    // 时间信息（宽表字段）
    // ============================================

    private final LocalDateTime createdAt;
    private final LocalDateTime startedAt;
    private final LocalDateTime endedAt;
    private final Long durationMillis;

    // ============================================
    // 版本和回滚信息（宽表字段）
    // ============================================

    private final Long previousVersion;      // 上一个版本
    private final Boolean rollbackIntent;    // 是否为回滚意图

    // ============================================
    // 重试信息（宽表字段）
    // ============================================

    private final Integer retryCount;
    private final Integer maxRetry;

    // ============================================
    // 私有构造函数
    // ============================================

    private TaskInfoView(Builder builder) {
        this.taskId = builder.taskId;
        this.tenantId = builder.tenantId;
        this.planId = builder.planId;
        this.deployUnitName = builder.deployUnitName;
        this.deployUnitId = builder.deployUnitId;
        this.deployUnitVersion = builder.deployUnitVersion;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.startedAt = builder.startedAt;
        this.endedAt = builder.endedAt;
        this.durationMillis = builder.durationMillis;
        this.previousVersion = builder.previousVersion;
        this.rollbackIntent = builder.rollbackIntent;
        this.retryCount = builder.retryCount;
        this.maxRetry = builder.maxRetry;
    }

    // ============================================
    // 静态工厂方法
    // ============================================

    /**
     * 从 TaskAggregate 创建完整视图
     * <p>
     * 事件在构造器中调用此方法：TaskInfoView.from(task)
     *
     * @param task Task 聚合根
     * @return TaskInfoView 实例
     */
    public static TaskInfoView from(TaskAggregate task) {
        if (task == null) {
            return null;
        }

        return builder()
            .taskId(task.getTaskId().getValue())
            .tenantId(task.getTenantId().getValue())
            .planId(task.getPlanId().getValue())
            .deployUnitName(task.getDeployUnitName())
            .deployUnitId(task.getDeployUnitId() != null ? task.getDeployUnitId().toString() : null)
            .deployUnitVersion(task.getDeployUnitVersion())
            .status(task.getStatus())
            .createdAt(task.getCreatedAt())
            .startedAt(task.getStartedAt())
            .endedAt(task.getEndedAt())
            .durationMillis(task.getDurationMillis())
            .previousVersion(task.getLastKnownGoodVersion())
            .rollbackIntent(task.isRollbackIntent() ? true : null)  // 只在为 true 时输出
            .retryCount(task.getRetryCount() > 0 ? task.getRetryCount() : null)
            .maxRetry(task.getMaxRetry())
            .build();
    }

    /**
     * 转换为简化的 TaskInfo（向后兼容）
     *
     * @return TaskInfo 实例
     */
    public TaskInfo toTaskInfo() {
        return new TaskInfo(
            TaskId.of(taskId),
            TenantId.of(tenantId),
            PlanId.of(planId),
            deployUnitName,
            deployUnitVersion,
            status
        );
    }

    // ============================================
    // Builder 模式
    // ============================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String taskId;
        private String tenantId;
        private String planId;
        private String deployUnitName;
        private String deployUnitId;
        private Long deployUnitVersion;
        private TaskStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private Long durationMillis;
        private Long previousVersion;
        private Boolean rollbackIntent;
        private Integer retryCount;
        private Integer maxRetry;

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder planId(String planId) {
            this.planId = planId;
            return this;
        }

        public Builder deployUnitName(String deployUnitName) {
            this.deployUnitName = deployUnitName;
            return this;
        }

        public Builder deployUnitId(String deployUnitId) {
            this.deployUnitId = deployUnitId;
            return this;
        }

        public Builder deployUnitVersion(Long deployUnitVersion) {
            this.deployUnitVersion = deployUnitVersion;
            return this;
        }

        public Builder status(TaskStatus status) {
            this.status = status;
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

        public Builder endedAt(LocalDateTime endedAt) {
            this.endedAt = endedAt;
            return this;
        }

        public Builder durationMillis(Long durationMillis) {
            this.durationMillis = durationMillis;
            return this;
        }

        public Builder previousVersion(Long previousVersion) {
            this.previousVersion = previousVersion;
            return this;
        }

        public Builder rollbackIntent(Boolean rollbackIntent) {
            this.rollbackIntent = rollbackIntent;
            return this;
        }

        public Builder retryCount(Integer retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder maxRetry(Integer maxRetry) {
            this.maxRetry = maxRetry;
            return this;
        }

        public TaskInfoView build() {
            return new TaskInfoView(this);
        }
    }

    // ============================================
    // Getter 方法
    // ============================================

    public String getTaskId() {
        return taskId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getPlanId() {
        return planId;
    }

    public String getDeployUnitName() {
        return deployUnitName;
    }

    public String getDeployUnitId() {
        return deployUnitId;
    }

    public Long getDeployUnitVersion() {
        return deployUnitVersion;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public Long getPreviousVersion() {
        return previousVersion;
    }

    public Boolean getRollbackIntent() {
        return rollbackIntent;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public Integer getMaxRetry() {
        return maxRetry;
    }

    @Override
    public String toString() {
        return String.format("TaskInfoView{taskId='%s', status=%s, progress=%s}",
            taskId, status,
            retryCount != null ? "retry=" + retryCount : "normal");
    }
}

