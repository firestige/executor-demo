package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.stage.StageStep;

import java.util.Objects;

/**
 * RF-19: Redis Pub/Sub 广播步骤（原子、可复用）
 *
 * 输入（来自 TaskRuntimeContext）：
 * - topic: String 必填，Redis 通道名
 * - message: String 必填，消息体（JSON 字符串或明文）
 */
public class MessageBroadcastStep implements StageStep {

    private static final Logger log = LoggerFactory.getLogger(MessageBroadcastStep.class);

    private final StringRedisTemplate redisTemplate;

    public MessageBroadcastStep(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate cannot be null");
    }

    @Override
    public String getStepName() {
        return "message-broadcast";
    }

    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        String topic = (String) ctx.getAdditionalData("topic");
        String message = (String) ctx.getAdditionalData("message");

        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required in TaskRuntimeContext");
        }
        if (message == null) {
            throw new IllegalArgumentException("message is required in TaskRuntimeContext");
        }

        Long receivers = redisTemplate.convertAndSend(topic, message);
        log.info("Message published: topic={}, receivers={}", topic, receivers);
    }
}
