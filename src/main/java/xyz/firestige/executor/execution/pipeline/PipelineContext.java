package xyz.firestige.executor.execution.pipeline;

import xyz.firestige.dto.deploy.TenantDeployConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Pipeline 上下文
 * 在 Pipeline 执行过程中传递数据
 */
public class PipelineContext {

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 租户部署配置
     */
    private TenantDeployConfig tenantDeployConfig;

    /**
     * 检查点数据（用于恢复）
     */
    private Map<String, Object> checkpointData;

    /**
     * 当前执行的 Stage
     */
    private String currentStage;

    /**
     * 累积的数据（Stage 之间传递）
     * Key: 数据键, Value: 数据值
     */
    private Map<String, Object> accumulatedData;

    /**
     * 是否暂停标志
     */
    private volatile boolean paused;

    /**
     * 是否取消标志
     */
    private volatile boolean cancelled;

    public PipelineContext() {
        this.checkpointData = new HashMap<>();
        this.accumulatedData = new HashMap<>();
        this.paused = false;
        this.cancelled = false;
    }

    public PipelineContext(String taskId, TenantDeployConfig tenantDeployConfig) {
        this();
        this.taskId = taskId;
        this.tenantDeployConfig = tenantDeployConfig;
    }

    /**
     * 放入累积数据
     */
    public void putData(String key, Object value) {
        this.accumulatedData.put(key, value);
    }

    /**
     * 获取累积数据
     */
    public Object getData(String key) {
        return this.accumulatedData.get(key);
    }

    /**
     * 获取累积数据（带类型转换）
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) {
        Object value = this.accumulatedData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 检查是否包含某个数据
     */
    public boolean containsData(String key) {
        return this.accumulatedData.containsKey(key);
    }

    /**
     * 检查是否应该暂停
     */
    public boolean shouldPause() {
        return paused;
    }

    /**
     * 检查是否应该取消
     */
    public boolean shouldCancel() {
        return cancelled;
    }

    /**
     * 请求暂停
     */
    public void requestPause() {
        this.paused = true;
    }

    /**
     * 恢复执行
     */
    public void resume() {
        this.paused = false;
    }

    /**
     * 请求取消
     */
    public void requestCancel() {
        this.cancelled = true;
    }

    // Getters and Setters

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public TenantDeployConfig getTenantDeployConfig() {
        return tenantDeployConfig;
    }

    public void setTenantDeployConfig(TenantDeployConfig tenantDeployConfig) {
        this.tenantDeployConfig = tenantDeployConfig;
    }

    public Map<String, Object> getCheckpointData() {
        return checkpointData;
    }

    public void setCheckpointData(Map<String, Object> checkpointData) {
        this.checkpointData = checkpointData;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public Map<String, Object> getAccumulatedData() {
        return accumulatedData;
    }

    public void setAccumulatedData(Map<String, Object> accumulatedData) {
        this.accumulatedData = accumulatedData;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}

