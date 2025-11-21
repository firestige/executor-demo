package xyz.firestige.deploy.infrastructure.execution.stage.validator;

/**
 * 验证结果
 * 用于表示 ResultValidator 的验证结果
 *
 * <p>包含：
 * <ul>
 *   <li>success: 验证是否成功</li>
 *   <li>message: 详细消息（成功或失败的原因）</li>
 *   <li>data: 可选的业务数据（扩展用）</li>
 * </ul>
 *
 * @since RF-19 三层抽象架构
 */
public class ValidationResult {

    private boolean success;
    private String message;
    private Object data;  // 可选的业务数据

    // Private constructor
    private ValidationResult() {
    }

    /**
     * 创建成功的验证结果
     *
     * @param message 成功消息
     * @return ValidationResult
     */
    public static ValidationResult success(String message) {
        ValidationResult result = new ValidationResult();
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }

    /**
     * 创建失败的验证结果
     *
     * @param message 失败原因
     * @return ValidationResult
     */
    public static ValidationResult failure(String message) {
        ValidationResult result = new ValidationResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    /**
     * 创建成功的验证结果（带数据）
     *
     * @param message 成功消息
     * @param data 业务数据
     * @return ValidationResult
     */
    public static ValidationResult successWithData(String message, Object data) {
        ValidationResult result = new ValidationResult();
        result.setSuccess(true);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}

