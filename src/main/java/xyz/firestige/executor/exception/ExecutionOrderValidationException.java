package xyz.firestige.executor.exception;

/**
 * 执行单验证异常
 * 执行单数据不合法时抛出
 */
public class ExecutionOrderValidationException extends TaskException {
    
    public ExecutionOrderValidationException(String message) {
        super(message);
    }
    
    public ExecutionOrderValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
