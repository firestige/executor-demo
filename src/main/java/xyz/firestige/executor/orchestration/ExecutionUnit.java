package xyz.firestige.executor.orchestration;

import xyz.firestige.dto.deploy.TenantDeployConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 执行单
 * 代表一组可以并发或顺序执行的租户任务
 */
@Deprecated
public class ExecutionUnit {

    /**
     * 执行单 ID
     */
    private String id;

    /**
     * 计划 ID
     */
    private Long planId;

    /**
     * 租户配置列表
     */
    private List<TenantDeployConfig> tenantConfigs;

    /**
     * 执行模式（CONCURRENT 或 FIFO）
     */
    private ExecutionMode executionMode;

    /**
     * 执行单状态
     */
    private ExecutionUnitStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 开始执行时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    public ExecutionUnit() {
        this.id = UUID.randomUUID().toString();
        this.tenantConfigs = new ArrayList<>();
        this.status = ExecutionUnitStatus.CREATED;
        this.createTime = LocalDateTime.now();
        this.executionMode = ExecutionMode.CONCURRENT; // 默认并发
    }

    public ExecutionUnit(Long planId, List<TenantDeployConfig> tenantConfigs, ExecutionMode executionMode) {
        this();
        this.planId = planId;
        this.tenantConfigs = tenantConfigs != null ? new ArrayList<>(tenantConfigs) : new ArrayList<>();
        this.executionMode = executionMode != null ? executionMode : ExecutionMode.CONCURRENT;
    }

    /**
     * 添加租户配置
     */
    public void addTenantConfig(TenantDeployConfig config) {
        if (config != null) {
            this.tenantConfigs.add(config);
        }
    }

    /**
     * 获取租户 ID 列表
     */
    public List<String> getTenantIds() {
        List<String> tenantIds = new ArrayList<>();
        for (TenantDeployConfig config : tenantConfigs) {
            if (config.getTenantId() != null) {
                tenantIds.add(config.getTenantId());
            }
        }
        return tenantIds;
    }

    /**
     * 获取租户数量
     */
    public int getTenantCount() {
        return tenantConfigs.size();
    }

    /**
     * 检查是否包含某个租户
     */
    public boolean containsTenant(String tenantId) {
        if (tenantId == null) {
            return false;
        }

        for (TenantDeployConfig config : tenantConfigs) {
            if (tenantId.equals(config.getTenantId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 标记为已调度
     */
    public void markAsScheduled() {
        this.status = ExecutionUnitStatus.SCHEDULED;
    }

    /**
     * 标记为运行中
     */
    public void markAsRunning() {
        this.status = ExecutionUnitStatus.RUNNING;
        this.startTime = LocalDateTime.now();
    }

    /**
     * 标记为已完成
     */
    public void markAsCompleted() {
        this.status = ExecutionUnitStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 标记为失败
     */
    public void markAsFailed() {
        this.status = ExecutionUnitStatus.FAILED;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 标记为已暂停
     */
    public void markAsPaused() {
        this.status = ExecutionUnitStatus.PAUSED;
    }

    /**
     * 标记为已取消
     */
    public void markAsCancelled() {
        this.status = ExecutionUnitStatus.CANCELLED;
        this.endTime = LocalDateTime.now();
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public List<TenantDeployConfig> getTenantConfigs() {
        return tenantConfigs;
    }

    public void setTenantConfigs(List<TenantDeployConfig> tenantConfigs) {
        this.tenantConfigs = tenantConfigs;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    public ExecutionUnitStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionUnitStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "ExecutionUnit{" +
                "id='" + id + '\'' +
                ", planId=" + planId +
                ", tenantCount=" + tenantConfigs.size() +
                ", executionMode=" + executionMode +
                ", status=" + status +
                '}';
    }
}
