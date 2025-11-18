package xyz.firestige.deploy.domain.task;

import org.slf4j.MDC;

/**
 * Task runtime context: MDC, pause/cancel flags, and pipeline context bridge.
 */
public class TaskRuntimeContext {
    private final String planId;
    private final String taskId;
    private final String tenantId;
    private volatile boolean pauseRequested;
    private volatile boolean cancelRequested;

    public TaskRuntimeContext(String planId, String taskId, String tenantId) {
        this.planId = planId;
        this.taskId = taskId;
        this.tenantId = tenantId;
    }

    public void injectMdc(String stageName) {
        MDC.put("planId", planId);
        MDC.put("taskId", taskId);
        MDC.put("tenantId", tenantId);
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

    public String getPlanId() { return planId; }
    public String getTaskId() { return taskId; }
    public String getTenantId() { return tenantId; }
}

