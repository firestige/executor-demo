package xyz.firestige.redis.ack.core;

import xyz.firestige.redis.ack.api.PubSubStageBuilder;
import xyz.firestige.redis.ack.api.VerifyStageBuilder;

import java.util.Map;
import java.util.function.Function;

/**
 * Pub/Sub 阶段构建器实现
 *
 * @author AI
 * @since 1.0
 */
public class PubSubStageBuilderImpl implements PubSubStageBuilder {

    private final WriteStageBuilderImpl writeStage;

    // Pub/Sub 配置
    private String topic;
    private Function<Object, String> messageBuilder;

    public PubSubStageBuilderImpl(WriteStageBuilderImpl writeStage) {
        this.writeStage = writeStage;
    }

    @Override
    public PubSubStageBuilder topic(String topic) {
        this.topic = topic;
        return this;
    }

    @Override
    public PubSubStageBuilder message(String message) {
        this.messageBuilder = v -> message;
        return this;
    }

    @Override
    public PubSubStageBuilder message(Function<Object, String> messageBuilder) {
        this.messageBuilder = messageBuilder;
        return this;
    }

    @Override
    public PubSubStageBuilder messageTemplate(String template) {
        this.messageBuilder = value -> {
            String result = template;

            // 支持 {fieldName} 占位符替换
            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String placeholder = "{" + entry.getKey() + "}";
                    if (result.contains(placeholder)) {
                        result = result.replace(placeholder, String.valueOf(entry.getValue()));
                    }
                }
            }

            return result;
        };
        return this;
    }

    @Override
    public VerifyStageBuilder andVerify() {
        validate();
        return new VerifyStageBuilderImpl(writeStage, this);
    }

    private void validate() {
        if (topic == null) {
            throw new IllegalStateException("topic is required");
        }
        if (messageBuilder == null) {
            throw new IllegalStateException("message is required");
        }
    }

    // Getters

    public WriteStageBuilderImpl getWriteStage() { return writeStage; }
    public String getTopic() { return topic; }
    public Function<Object,String> getMessageBuilder() { return messageBuilder; }
}
