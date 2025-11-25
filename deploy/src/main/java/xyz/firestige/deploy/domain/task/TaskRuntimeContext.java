package xyz.firestige.deploy.domain.task;

import org.slf4j.MDC;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

import java.util.HashMap;
import java.util.Map;

/**
 * Task runtime context: MDC, pause/cancel flags, and pipeline context bridge.
 */
public class TaskRuntimeContext {
    private final PlanId planId;
    private final TaskId taskId;
    private final TenantId tenantId;
    private final Map<String, Object> context;
    private volatile boolean pauseRequested;
    private volatile boolean cancelRequested;

    public TaskRuntimeContext(PlanId planId, TaskId taskId, TenantId tenantId) {
        this.planId = planId;
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.context = new HashMap<>();
    }

    public void injectMdc(String stageName) {
        MDC.put("planId", planId.getValue());
        MDC.put("taskId", taskId.getValue());
        MDC.put("tenantId", tenantId.getValue());
        if (stageName != null) {
            MDC.put("stageName", stageName);
        }
    }

    public void clearMdc() { MDC.clear(); }

    public boolean isPauseRequested() { return pauseRequested; }
    public void requestPause() { this.pauseRequested = true; }
    public void clearPause() { this.pauseRequested = false; }

    public boolean isCancelRequested() { return cancelRequested; }
    public void requestCancel() { this.cancelRequested = true; }
    public void clearCancel() { this.cancelRequested = false; }

    public PlanId getPlanId() { return planId; }
    public TaskId getTaskId() { return taskId; }
    public TenantId getTenantId() { return tenantId; }

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

    public void addVariable(String key, Object value) { context.put(key, value); }
}

