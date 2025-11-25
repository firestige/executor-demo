package xyz.firestige.redis.ack.spring.autoconfigure;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import xyz.firestige.redis.ack.api.RedisAckService;

/**
 * Redis ACK 服务健康检查指示器
 *
 * @author AI
 * @since 1.0
 */
public class RedisAckHealthIndicator implements HealthIndicator {

    private final RedisAckService ackService;

    public RedisAckHealthIndicator(RedisAckService ackService) {
        this.ackService = ackService;
    }

    @Override
    public Health health() {
        try {
            // 检查服务是否可用
            if (ackService != null) {
                return Health.up()
                    .withDetail("service", "RedisAckService")
                    .withDetail("status", "Available")
                    .build();
            }

            return Health.unknown()
                .withDetail("service", "RedisAckService")
                .withDetail("status", "Service is null")
                .build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("service", "RedisAckService")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}

