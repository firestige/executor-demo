package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.event.WithFailureInfo;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.validation.ValidationError;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskErrorView;
import xyz.firestige.deploy.domain.task.TaskInfoView;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务校验失败事件
 * <p>
 * T-036: 重构为使用三大视图
 */
public class TaskValidationFailedEvent extends TaskStatusEvent implements WithFailureInfo {

    private final TaskInfoView taskInfo;
    private final TaskErrorView error;
    private final List<ValidationError> validationErrors;
    private final List<TenantConfig> invalidConfigs;
    private final FailureInfo failureInfo;

    public TaskValidationFailedEvent(TaskAggregate task, List<ValidationError> validationErrors,
                                    List<TenantConfig> invalidConfigs, FailureInfo failureInfo) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.error = TaskErrorView.from(failureInfo);
        this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>();
        this.invalidConfigs = invalidConfigs != null ? invalidConfigs : new ArrayList<>();
        this.failureInfo = failureInfo;
        setMessage("任务校验失败，错误数: " + this.validationErrors.size());
    }

    public TaskInfoView getTaskInfoView() { return taskInfo; }
    public TaskErrorView getError() { return error; }

    // Getters and Setters

    @Override
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

