package xyz.firestige.redis.ack.spring.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.redis.ack.api.RedisAckService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 redis.ack.enabled=false 时不装配 RedisAckService
 */
class RedisAckAutoConfigurationDisabledTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisAckAutoConfiguration.class))
        .withUserConfiguration(MockRedisConfig.class)
        .withPropertyValues(
            "redis.ack.enabled=false"
        );

    @Test
    void shouldNotCreateAckServiceWhenDisabled() {
        contextRunner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(RedisAckService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class MockRedisConfig {
        @Bean
        @SuppressWarnings("unchecked")
        RedisTemplate<String,String> redisTemplate() {
            return new RedisTemplate<>();
        }
    }
}

