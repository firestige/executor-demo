package xyz.firestige.executor.config;

/**
 * 全局配置（通过 application 配置覆盖默认值）。
 * 优先级：TenantDeployConfig > application config > 内置默认
 */
public class ExecutorProperties {
    private int maxConcurrency = 10; // Plan 默认并发阈值
    private int maxRetry = 3;        // Task 默认最大重试次数
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
}
