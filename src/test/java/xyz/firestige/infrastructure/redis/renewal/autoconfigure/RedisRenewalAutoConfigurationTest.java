package xyz.firestige.infrastructure.redis.renewal.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.infrastructure.redis.renewal.api.KeyRenewalService;
import xyz.firestige.infrastructure.redis.renewal.api.RedisClient;
import xyz.firestige.infrastructure.redis.renewal.core.AsyncRenewalExecutor;
import xyz.firestige.infrastructure.redis.renewal.metrics.RenewalMetricsCollector;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRenewalAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisRenewalAutoConfiguration.class))
        .withBean(RedisTemplate.class, () -> {
            // Mock RedisTemplate
            return new RedisTemplate<>();
        });

    @Test
    void autoConfiguration_whenEnabled_createsAllBeans() {
        contextRunner
            .withPropertyValues("redis.renewal.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(RedisClient.class);
                assertThat(context).hasSingleBean(AsyncRenewalExecutor.class);
                assertThat(context).hasSingleBean(KeyRenewalService.class);
                assertThat(context).hasSingleBean(RenewalMetricsCollector.class);
            });
    }

    @Test
    void autoConfiguration_whenDisabled_doesNotCreateBeans() {
        contextRunner
            .withPropertyValues("redis.renewal.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(KeyRenewalService.class);
            });
    }

    @Test
    void autoConfiguration_customProperties_appliedCorrectly() {
        contextRunner
            .withPropertyValues(
                "redis.renewal.enabled=true",
                "redis.renewal.executor-thread-pool-size=8",
                "redis.renewal.time-wheel.tick-duration=50"
            )
            .run(context -> {
                RedisRenewalProperties properties = context.getBean(RedisRenewalProperties.class);
                assertThat(properties.getExecutorThreadPoolSize()).isEqualTo(8);
                assertThat(properties.getTimeWheel().getTickDuration()).isEqualTo(50);
            });
    }
}

