package xyz.firestige.infrastructure.redis.ack.core;

import xyz.firestige.infrastructure.redis.ack.api.PubSubStageBuilder;
import xyz.firestige.infrastructure.redis.ack.api.VerifyStageBuilder;

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
            // 简单的模板替换实现
            // TODO: Phase 3 可以增强为支持复杂占位符
            return template;
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

    String getTopic() { return topic; }
    Function<Object, String> getMessageBuilder() { return messageBuilder; }
    WriteStageBuilderImpl getWriteStage() { return writeStage; }
}

