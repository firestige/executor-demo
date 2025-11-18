package xyz.firestige.deploy.exception;

/**
 * 状态转移异常
 * 任务状态转移失败时抛出
 */
public class StateTransitionException extends ExecutorException {

    private String fromStatus;
    private String toStatus;

    public StateTransitionException(String message) {
        super(message);
        setErrorType(ErrorType.SYSTEM_ERROR);
        setRetryable(false);
    }

    public StateTransitionException(String message, String fromStatus, String toStatus) {
        super(message);
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        setErrorType(ErrorType.SYSTEM_ERROR);
        setRetryable(false);
        addContext("fromStatus", fromStatus);
        addContext("toStatus", toStatus);
    }

    public StateTransitionException(String message, Throwable cause) {
        super(message, cause);
        setErrorType(ErrorType.SYSTEM_ERROR);
        setRetryable(false);
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }
}

