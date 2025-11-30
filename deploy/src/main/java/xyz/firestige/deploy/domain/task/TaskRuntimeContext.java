package xyz.firestige.deploy.domain.task;

import org.slf4j.MDC;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

import java.util.HashMap;
import java.util.Map;

/**
 * Task runtime context: MDC, pause/cancel flags, and pipeline context bridge.
 *
 * @since T-032 优化版：增加重试和回滚标志位支持 + 执行信息
 */
public class TaskRuntimeContext {
    private final PlanId planId;
    private final TaskId taskId;
    private final TenantId tenantId;
    private final Map<String, Object> context;

    // 已有的标志位
    private volatile boolean pauseRequested;
    private volatile boolean cancelRequested;

    // ✅ T-032: 新增重试标志位
    private volatile boolean retryRequested;
    private volatile boolean fromCheckpoint;

    // ✅ T-032: 新增回滚标志位
    private volatile boolean rollbackRequested;
    private volatile String rollbackTargetVersion;

    // ✅ T-032 优化：新增执行信息（由 ExecutionPreparer 设置）
    private volatile int startIndex;           // Stage 起点索引
    private volatile ExecutionMode executionMode;  // 执行模式

    public TaskRuntimeContext(PlanId planId, TaskId taskId, TenantId tenantId) {
        this.planId = planId;
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.context = new HashMap<>();
        this.startIndex = 0;
        this.executionMode = ExecutionMode.NORMAL;
    }

    public void injectMdc(String stageName) {
        MDC.put("planId", planId.getValue());
        MDC.put("taskId", taskId.getValue());
        MDC.put("tenantId", tenantId.getValue());
        if (stageName != null) {
            MDC.put("stageName", stageName);
        }
    }

    public void clearMdc() {
        MDC.clear();
    }

    public boolean isPauseRequested() {
        return pauseRequested;
    }

    public void requestPause() {
        this.pauseRequested = true;
    }

    public void clearPause() {
        this.pauseRequested = false;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public void requestCancel() {
        this.cancelRequested = true;
    }

    public void clearCancel() {
        this.cancelRequested = false;
    }

    // ========== T-032: 重试标志位方法 ==========
    /**
     * 是否请求重试
     */
    public boolean isRetryRequested() {
        return retryRequested;
    }

    /**
     * 是否从检查点重试
     */
    public boolean isFromCheckpoint() {
        return fromCheckpoint;
    }

    /**
     * 请求重试
     *
     * @param fromCheckpoint 是否从检查点恢复
     */
    public void requestRetry(boolean fromCheckpoint) {
        this.retryRequested = true;
        this.fromCheckpoint = fromCheckpoint;
    }

    /**
     * 清除重试标志
     */
    public void clearRetry() {
        this.retryRequested = false;
        this.fromCheckpoint = false;
    }

    // ========== T-032: 回滚标志位方法 ==========
    /**
     * 是否请求回滚
     */
    public boolean isRollbackRequested() {
        return rollbackRequested;
    }

    /**
     * 获取回滚目标版本
     */
    public String getRollbackTargetVersion() {
        return rollbackTargetVersion;
    }

    /**
     * 请求回滚
     *
     * @param targetVersion 回滚目标版本（planVersion）
     */
    public void requestRollback(String targetVersion) {
        this.rollbackRequested = true;
        this.rollbackTargetVersion = targetVersion;
    }

    /**
     * 清除回滚标志
     */
    public void clearRollback() {
        this.rollbackRequested = false;
        this.rollbackTargetVersion = null;
    }

    // ========== T-032 优化：执行信息方法 ==========
    /**
     * 获取 Stage 起点索引
     */
    public int getStartIndex() {
        return startIndex;
    }

    /**
     * 设置 Stage 起点索引
     * <p>
     * 由 ExecutionPreparer 调用
     */
    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    /**
     * 获取执行模式
     */
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    /**
     * 设置执行模式
     * <p>
     * 由 ExecutionPreparer 调用
     */
    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    /**
     * 是否回滚模式
     */
    public boolean isRollbackMode() {
        return executionMode == ExecutionMode.ROLLBACK;
    }

    /**
     * 是否正常模式
     */
    public boolean isNormalMode() {
        return executionMode == ExecutionMode.NORMAL;
    }

    public PlanId getPlanId() {
        return planId;
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public Object getAdditionalData(String key) {
        return context.get(key);
    }

    public <T> T getAdditionalData(String key, Class<T> clazz) {
        Object v = context.get(key);
        return clazz.isInstance(v) ? clazz.cast(v) : null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getAdditionalData(String key, T defaultValue) {
        Object v = context.get(key);
        Class<?> clazz = defaultValue.getClass();
        return clazz.isInstance(v) ? (T) v : defaultValue;
    }

    public void addVariable(String key, Object value) {
        context.put(key, value);
    }

    /**
     * 执行模式枚举
     */
    public enum ExecutionMode {
        /** 正常执行模式（正序执行 Stages） */
        NORMAL,

        /** 回滚模式（逆序执行 Stages） */
        ROLLBACK
    }
}
