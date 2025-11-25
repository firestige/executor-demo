package xyz.firestige.deploy.domain.task.exception;

import xyz.firestige.deploy.domain.shared.exception.ErrorType;

/**
 * 检查点异常
 * 检查点保存或加载失败时抛出
 */
public class CheckpointException extends ExecutorException {

    private String taskId;
    private String stageName;

    public CheckpointException(String message) {
        super(message);
        setErrorType(ErrorType.SYSTEM_ERROR);
    }

    public CheckpointException(String message, String taskId) {
        super(message);
        this.taskId = taskId;
        setErrorType(ErrorType.SYSTEM_ERROR);
        addContext("taskId", taskId);
    }

    public CheckpointException(String message, String taskId, String stageName) {
        super(message);
        this.taskId = taskId;
        this.stageName = stageName;
        setErrorType(ErrorType.SYSTEM_ERROR);
        addContext("taskId", taskId);
        addContext("stageName", stageName);
    }

    public CheckpointException(String message, Throwable cause) {
        super(message, cause);
        setErrorType(ErrorType.SYSTEM_ERROR);
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }
}

