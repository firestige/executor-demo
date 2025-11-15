package xyz.firestige.executor.domain.task;

import org.slf4j.MDC;
import xyz.firestige.executor.execution.pipeline.PipelineContext;

/**
 * Task 执行上下文，包装 PipelineContext 并承担 MDC 管理
 */
public class TaskContext {
    private final String planId;
    private final String taskId;
    private final String tenantId;

    private final PipelineContext pipelineContext;

    private volatile boolean pauseRequested;
    private volatile boolean cancelRequested;

    public TaskContext(String planId, String taskId, String tenantId, PipelineContext pipelineContext) {
        this.planId = planId;
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.pipelineContext = pipelineContext;
    }

    public TaskContext(String taskId) { // 简化构造用于测试/早期接线
        this(null, taskId, null, new xyz.firestige.executor.execution.pipeline.PipelineContext());
    }

    public void injectMdc(String stageName) {
        MDC.put("planId", planId);
        MDC.put("taskId", taskId);
        MDC.put("tenantId", tenantId);
        if (stageName != null) {
            MDC.put("stageName", stageName);
        }
    }

    public void clearMdc() {
        MDC.clear();
    }

    public PipelineContext getPipelineContext() { return pipelineContext; }

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
