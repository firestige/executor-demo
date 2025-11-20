package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
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
    private final List<ValidationError> validationErrors;

    /**
     * 无效的配置列表
     */
    private final List<TenantConfig> invalidConfigs;

    private final FailureInfo failureInfo;

    public TaskValidationFailedEvent(TaskInfo info, FailureInfo failureInfo, List<ValidationError> validationErrors) {
        super(info);
        this.failureInfo = failureInfo;
        this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>();
        this.invalidConfigs = new ArrayList<>();
        setMessage("任务校验失败，错误数量: " + this.validationErrors.size());
    }

    // Getters and Setters

    public FailureInfo getFailureInfo() {
        return failureInfo;
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public List<TenantConfig> getInvalidConfigs() {
        return invalidConfigs;
    }

    public void addInvalidConfig(TenantConfig invalidConfig) {
        this.invalidConfigs.add(invalidConfig);
    }

    public void addInvalidConfigs(List<TenantConfig> invalidConfigs) {
        this.invalidConfigs.addAll(invalidConfigs);
    }
}

