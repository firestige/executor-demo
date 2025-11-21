package xyz.firestige.deploy.infrastructure.execution.stage.redis;

/**
 * Redis 配置写入数据
 * 用于 ConfigWriteStep 的输入数据
 *
 * @since RF-19 三层抽象架构
 */
public class ConfigWriteData {

    private String key;
    private String field;
    private String value;

    private ConfigWriteData() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ConfigWriteData data = new ConfigWriteData();

        public Builder key(String key) {
            data.key = key;
            return this;
        }

        public Builder field(String field) {
            data.field = field;
            return this;
        }

        public Builder value(String value) {
            data.value = value;
            return this;
        }

        public ConfigWriteData build() {
            if (data.key == null || data.key.isEmpty()) {
                throw new IllegalArgumentException("key is required");
            }
            if (data.field == null || data.field.isEmpty()) {
                throw new IllegalArgumentException("field is required");
            }
            if (data.value == null) {
                throw new IllegalArgumentException("value is required");
            }
            return data;
        }
    }

    // Getters

    public String getKey() {
        return key;
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }
}

