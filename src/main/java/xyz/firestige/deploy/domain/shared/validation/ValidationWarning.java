package xyz.firestige.deploy.domain.shared.validation;

/**
 * 校验警告
 */
public class ValidationWarning {

    /**
     * 警告字段
     */
    private String field;

    /**
     * 警告消息
     */
    private String message;

    /**
     * 警告码
     */
    private String warningCode;

    public ValidationWarning() {
    }

    public ValidationWarning(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public ValidationWarning(String field, String message, String warningCode) {
        this.field = field;
        this.message = message;
        this.warningCode = warningCode;
    }

    public static ValidationWarning of(String field, String message) {
        return new ValidationWarning(field, message);
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

    public String getWarningCode() {
        return warningCode;
    }

    public void setWarningCode(String warningCode) {
        this.warningCode = warningCode;
    }

    @Override
    public String toString() {
        return "ValidationWarning{" +
                "field='" + field + '\'' +
                ", message='" + message + '\'' +
                ", warningCode='" + warningCode + '\'' +
                '}';
    }
}

