package xyz.firestige.redis.ack.spring.autoconfigure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.redis.ack.api.AckMetricsRecorder;
import xyz.firestige.redis.ack.api.RedisAckService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 redis.ack.enabled=true 时自动装配成功
 */
class RedisAckAutoConfigurationEnabledTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisAckAutoConfiguration.class))
        .withUserConfiguration(MockRedisConfig.class)
        .withPropertyValues(
            "redis.ack.enabled=true",
            "redis.ack.http.connect-timeout=2s",
            "redis.ack.http.read-timeout=3s"
        );

    @Test
    void shouldCreateAckServiceWhenEnabled() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(RedisAckService.class);
            assertThat(ctx).hasBean("ackRestTemplate");
        });
    }

    @Test
    void shouldCreateMetricsRecorderWhenMeterRegistryPresent() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAckAutoConfiguration.class))
            .withUserConfiguration(MockRedisConfig.class)
            .withUserConfiguration(MetricsConfig.class)
            .withPropertyValues("redis.ack.enabled=true")
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(AckMetricsRecorder.class);
                assertThat(ctx.getBean(AckMetricsRecorder.class)).isNotNull();
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

    @Configuration(proxyBeanMethods = false)
    static class MetricsConfig {
        @Bean
        SimpleMeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }
}

