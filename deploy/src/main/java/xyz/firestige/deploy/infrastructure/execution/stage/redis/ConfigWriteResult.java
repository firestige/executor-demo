package xyz.firestige.deploy.infrastructure.execution.stage.redis;

/**
 * Redis 配置写入结果
 * 用于 ConfigWriteStep 的输出数据
 *
 * @since RF-19 三层抽象架构
 */
public class ConfigWriteResult {

    private boolean success;
    private String key;
    private String field;
    private String message;

    private ConfigWriteResult() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ConfigWriteResult result = new ConfigWriteResult();

        public Builder success(boolean success) {
            result.success = success;
            return this;
        }

        public Builder key(String key) {
            result.key = key;
            return this;
        }

        public Builder field(String field) {
            result.field = field;
            return this;
        }

        public Builder message(String message) {
            result.message = message;
            return this;
        }

        public ConfigWriteResult build() {
            return result;
        }
    }

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

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
}

