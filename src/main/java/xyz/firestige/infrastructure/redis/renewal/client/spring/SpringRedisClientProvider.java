package xyz.firestige.infrastructure.redis.renewal.client.spring;

import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.infrastructure.redis.renewal.api.RedisClient;
import xyz.firestige.infrastructure.redis.renewal.client.spi.RedisClientProvider;

/**
 * Spring Redis 客户端提供者（SPI 实现）
 *
 * <p>用于非 Spring Boot 环境通过 SPI 加载 Spring Data Redis 客户端。
 *
 * <h3>注意</h3>
 * <p>此实现需要手动提供 {@link RedisTemplate} 实例。
 * 在 Spring Boot 环境中，推荐使用 AutoConfiguration 而不是 SPI。
 *
 * @author T-018
 * @since 1.0.0
 */
public class SpringRedisClientProvider implements RedisClientProvider {

    private RedisTemplate<String, String> redisTemplate;

    /**
     * 默认构造函数（SPI 需要）
     */
    public SpringRedisClientProvider() {
    }

    /**
     * 设置 RedisTemplate
     *
     * <p>必须在调用 {@link #createClient()} 之前设置。
     *
     * @param redisTemplate Redis 模板
     */
    public void setRedisTemplate(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getName() {
        return "spring";
    }

    @Override
    public RedisClient createClient() {
        if (redisTemplate == null) {
            throw new IllegalStateException(
                "RedisTemplate 未设置，请先调用 setRedisTemplate()"
            );
        }
        return new SpringRedisClient(redisTemplate);
    }
}

