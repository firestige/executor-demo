package xyz.firestige.deploy.config;

import xyz.firestige.deploy.support.conflict.TenantConflictManager;

/**
 * 全局配置（通过 application 配置覆盖默认值）。
 * 优先级：TenantDeployConfig > application config > 内置默认
 */
public class ExecutorProperties {
    private int maxConcurrency = 10; // Plan 默认并发阈值
    private int maxRetry = 3;        // Task 默认最大重试次数
    
    // RF-14: 冲突检测策略（合并 PlanSchedulingStrategy）
    private TenantConflictManager.ConflictPolicy conflictPolicy = TenantConflictManager.ConflictPolicy.FINE_GRAINED;
    
    // 向后兼容：保留旧配置字段（已废弃）
    @Deprecated
    private String schedulingStrategy = "fine-grained";
    private int healthCheckIntervalSeconds = 3;
    private int healthCheckMaxAttempts = 10;
    private int taskProgressIntervalSeconds = 10;
    private String healthCheckPath = "/health"; // 新增：健康检查路径（可覆盖）
    private String healthCheckVersionKey = "version"; // 新增：健康检查版本键（可覆盖）

    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }

    public int getMaxRetry() { return maxRetry; }
    public void setMaxRetry(int maxRetry) { this.maxRetry = maxRetry; }

    public int getHealthCheckIntervalSeconds() { return healthCheckIntervalSeconds; }
    public void setHealthCheckIntervalSeconds(int v) { this.healthCheckIntervalSeconds = v; }

    public int getHealthCheckMaxAttempts() { return healthCheckMaxAttempts; }
    public void setHealthCheckMaxAttempts(int v) { this.healthCheckMaxAttempts = v; }

    public int getTaskProgressIntervalSeconds() { return taskProgressIntervalSeconds; }
    public void setTaskProgressIntervalSeconds(int v) { this.taskProgressIntervalSeconds = v; }

    public String getHealthCheckPath() { return healthCheckPath; }
    public void setHealthCheckPath(String healthCheckPath) { this.healthCheckPath = healthCheckPath; }

    public String getHealthCheckVersionKey() { return healthCheckVersionKey; }
    public void setHealthCheckVersionKey(String healthCheckVersionKey) { this.healthCheckVersionKey = healthCheckVersionKey; }

    // RF-14: 冲突策略配置
    public TenantConflictManager.ConflictPolicy getConflictPolicy() { return conflictPolicy; }
    public void setConflictPolicy(TenantConflictManager.ConflictPolicy conflictPolicy) { 
        this.conflictPolicy = conflictPolicy; 
    }

    /**
     * 向后兼容：从旧配置映射到新策略
     * @deprecated 使用 {@link #setConflictPolicy(TenantConflictManager.ConflictPolicy)} 替代
     */
    @Deprecated
    public void setSchedulingStrategy(String strategy) {
        this.schedulingStrategy = strategy;
        // 自动映射旧配置到新枚举
        if ("coarse-grained".equals(strategy)) {
            this.conflictPolicy = TenantConflictManager.ConflictPolicy.COARSE_GRAINED;
        } else {
            this.conflictPolicy = TenantConflictManager.ConflictPolicy.FINE_GRAINED;
        }
    }

    /**
     * @deprecated 使用 {@link #getConflictPolicy()} 替代
     */
    @Deprecated
    public String getSchedulingStrategy() {
        return schedulingStrategy;
    }
}
