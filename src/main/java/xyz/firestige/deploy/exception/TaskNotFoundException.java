package xyz.firestige.deploy.exception;

/**
 * 任务不存在异常
 * 查找任务时未找到对应任务时抛出
 */
public class TaskNotFoundException extends ExecutorException {

    private String taskId;
    private String tenantId;
    private Long planId;

    public TaskNotFoundException(String message) {
        super(message);
        setErrorType(ErrorType.BUSINESS_ERROR);
        setRetryable(false);
    }

    public TaskNotFoundException(String message, String taskId) {
        super(message);
        this.taskId = taskId;
        setErrorType(ErrorType.BUSINESS_ERROR);
        setRetryable(false);
        addContext("taskId", taskId);
    }

    public TaskNotFoundException(String message, String tenantId, Long planId) {
        super(message);
        this.tenantId = tenantId;
        this.planId = planId;
        setErrorType(ErrorType.BUSINESS_ERROR);
        setRetryable(false);
        addContext("tenantId", tenantId);
        addContext("planId", planId);
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }
}

