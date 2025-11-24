package xyz.firestige.infrastructure.redis.ack.api;

import java.util.function.Function;

/**
 * Pub/Sub 阶段构建器
 * <p>
 * 负责配置 Redis Pub/Sub 通知
 *
 * @author AI
 * @since 1.0
 */
public interface PubSubStageBuilder {

    /**
     * 设置发布的 Topic
     *
     * @param topic Topic 名称
     * @return this
     */
    PubSubStageBuilder topic(String topic);

    /**
     * 设置固定的消息内容
     *
     * @param message 消息内容
     * @return this
     */
    PubSubStageBuilder message(String message);

    /**
     * 使用函数构建消息内容
     *
     * @param messageBuilder 消息构建函数，接收写入的 value
     * @return this
     */
    PubSubStageBuilder message(Function<Object, String> messageBuilder);

    /**
     * 使用模板构建消息（支持占位符）
     *
     * @param template 模板字符串，例如 "CONFIG_UPDATED:{tenantId}:{version}"
     * @return this
     */
    PubSubStageBuilder messageTemplate(String template);

    /**
     * 进入验证阶段（必须调用）
     *
     * @return Verify 阶段构建器
     */
    VerifyStageBuilder andVerify();
}

