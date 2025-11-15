package xyz.firestige.executor.orchestration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 执行单结果
 */
@Deprecated
public class ExecutionUnitResult {

    /**
     * 执行单 ID
     */
    private String executionUnitId;

    /**
     * 执行单状态
     */
    private ExecutionUnitStatus status;

    /**
     * 租户任务执行结果映射
     * Key: tenantId, Value: PipelineResult
     */
    private Map<String, Object> tenantResults;

    /**
     * 成功数量
     */
    private int successCount;

    /**
     * 失败数量
     */
    private int failureCount;

    /**
     * 总耗时
     */
    private Duration duration;

    public ExecutionUnitResult() {
        this.tenantResults = new HashMap<>();
    }

    public ExecutionUnitResult(String executionUnitId) {
        this.executionUnitId = executionUnitId;
        this.tenantResults = new HashMap<>();
    }

    /**
     * 添加租户结果
     */
    public void addTenantResult(String tenantId, Object result) {
        this.tenantResults.put(tenantId, result);
    }

    /**
     * 增加成功计数
     */
    public void incrementSuccess() {
        this.successCount++;
    }

    /**
     * 增加失败计数
     */
    public void incrementFailure() {
        this.failureCount++;
    }

    /**
     * 获取总数
     */
    public int getTotalCount() {
        return successCount + failureCount;
    }

    /**
     * 是否全部成功
     */
    public boolean isAllSuccess() {
        return failureCount == 0 && successCount > 0;
    }

    /**
     * 是否有失败
     */
    public boolean hasFailure() {
        return failureCount > 0;
    }

    // Getters and Setters

    public String getExecutionUnitId() {
        return executionUnitId;
    }

    public void setExecutionUnitId(String executionUnitId) {
        this.executionUnitId = executionUnitId;
    }

    public ExecutionUnitStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionUnitStatus status) {
        this.status = status;
    }

    public Map<String, Object> getTenantResults() {
        return tenantResults;
    }

    public void setTenantResults(Map<String, Object> tenantResults) {
        this.tenantResults = tenantResults;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    @Override
    public String toString() {
        return "ExecutionUnitResult{" +
                "executionUnitId='" + executionUnitId + '\'' +
                ", status=" + status +
                ", successCount=" + successCount +
                ", failureCount=" + failureCount +
                ", duration=" + duration +
                '}';
    }
}
