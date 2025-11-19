package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.domain.shared.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务校验失败事件
 */
public class TaskValidationFailedEvent extends TaskStatusEvent {

    /**
     * 校验错误列表
     */
    private List<ValidationError> validationErrors;

    /**
     * 无效的配置列表
     */
    private List<TenantDeployConfig> invalidConfigs;

    public TaskValidationFailedEvent() {
        super();
        setStatus(TaskStatus.VALIDATION_FAILED);
        this.validationErrors = new ArrayList<>();
        this.invalidConfigs = new ArrayList<>();
    }

    public TaskValidationFailedEvent(TaskId taskId, FailureInfo failureInfo, List<ValidationError> validationErrors) {
        super(taskId, TaskStatus.VALIDATION_FAILED);
        setFailureInfo(failureInfo);
        this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>();
        this.invalidConfigs = new ArrayList<>();
        setMessage("任务校验失败，错误数量: " + this.validationErrors.size());
    }

    // Getters and Setters

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public List<TenantDeployConfig> getInvalidConfigs() {
        return invalidConfigs;
    }

    public void setInvalidConfigs(List<TenantDeployConfig> invalidConfigs) {
        this.invalidConfigs = invalidConfigs;
    }
}

