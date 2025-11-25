package xyz.firestige.infrastructure.redis.ack.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.infrastructure.redis.ack.api.RedisAckService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Redis ACK 自动配置测试
 */
class RedisAckAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisAckAutoConfiguration.class))
        .withBean(RedisTemplate.class, () -> {
            @SuppressWarnings("unchecked")
            RedisTemplate<String, String> template = mock(RedisTemplate.class);
            return template;
        });

    @Test
    void autoConfiguration_whenEnabled_createsService() {
        contextRunner
            .withPropertyValues("redis.ack.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(RedisAckService.class);
                assertThat(context).hasBean("ackRestTemplate");
            });
    }

    @Test
    void autoConfiguration_whenDisabled_doesNotCreateService() {
        contextRunner
            .withPropertyValues("redis.ack.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(RedisAckService.class);
            });
    }

    @Test
    void properties_defaultValues_loaded() {
        contextRunner
            .withPropertyValues("redis.ack.enabled=true")
            .run(context -> {
                RedisAckProperties properties = context.getBean(RedisAckProperties.class);
                assertThat(properties.isEnabled()).isTrue();
                assertThat(properties.getDefaultTimeout().getSeconds()).isEqualTo(60);
                assertThat(properties.getDefaultRetry().getMaxAttempts()).isEqualTo(10);
            });
    }

    @Test
    void properties_customValues_loaded() {
        contextRunner
            .withPropertyValues(
                "redis.ack.enabled=true",
                "redis.ack.default-timeout=120s",
                "redis.ack.default-retry.max-attempts=20",
                "redis.ack.http.connect-timeout=10s"
            )
            .run(context -> {
                RedisAckProperties properties = context.getBean(RedisAckProperties.class);
                assertThat(properties.getDefaultTimeout().getSeconds()).isEqualTo(120);
                assertThat(properties.getDefaultRetry().getMaxAttempts()).isEqualTo(20);
                assertThat(properties.getHttp().getConnectTimeout().getSeconds()).isEqualTo(10);
            });
    }
}

