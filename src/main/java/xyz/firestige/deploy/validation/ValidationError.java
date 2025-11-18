package xyz.firestige.deploy.validation;

/**
 * 校验错误
 */
public class ValidationError {

    /**
     * 错误字段
     */
    private String field;

    /**
     * 错误消息
     */
    private String message;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 被拒绝的值
     */
    private Object rejectedValue;

    public ValidationError() {
    }

    public ValidationError(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public ValidationError(String field, String message, String errorCode) {
        this.field = field;
        this.message = message;
        this.errorCode = errorCode;
    }

    public ValidationError(String field, String message, String errorCode, Object rejectedValue) {
        this.field = field;
        this.message = message;
        this.errorCode = errorCode;
        this.rejectedValue = rejectedValue;
    }

    public static ValidationError of(String field, String message) {
        return new ValidationError(field, message);
    }

    public static ValidationError of(String field, String message, Object rejectedValue) {
        return new ValidationError(field, message, null, rejectedValue);
    }

    // Getters and Setters

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }

    public void setRejectedValue(Object rejectedValue) {
        this.rejectedValue = rejectedValue;
    }

    @Override
    public String toString() {
        return "ValidationError{" +
                "field='" + field + '\'' +
                ", message='" + message + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", rejectedValue=" + rejectedValue +
                '}';
    }
}

