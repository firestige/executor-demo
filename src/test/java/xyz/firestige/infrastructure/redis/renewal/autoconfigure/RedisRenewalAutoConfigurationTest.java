package xyz.firestige.infrastructure.redis.renewal.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import xyz.firestige.infrastructure.redis.renewal.api.KeyRenewalService;
import xyz.firestige.infrastructure.redis.renewal.api.RedisClient;
import xyz.firestige.infrastructure.redis.renewal.core.AsyncRenewalExecutor;
import xyz.firestige.infrastructure.redis.renewal.metrics.RenewalMetricsCollector;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRenewalAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisRenewalAutoConfiguration.class))
        .withBean(RedisClient.class, MockRedisClient::new);

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
                assertThat(context).doesNotHaveBean(AsyncRenewalExecutor.class);
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

    /**
     * Mock RedisClient 用于测试
     */
    static class MockRedisClient implements RedisClient {
        @Override
        public boolean expire(String key, long ttlSeconds) {
            return true;
        }

        @Override
        public Map<String, Boolean> batchExpire(Collection<String> keys, long ttlSeconds) {
            return Map.of();
        }

        @Override
        public CompletableFuture<Boolean> expireAsync(String key, long ttlSeconds) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Map<String, Boolean>> batchExpireAsync(Collection<String> keys, long ttlSeconds) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public Collection<String> scan(String pattern, int count) {
            return List.of();
        }

        @Override
        public boolean exists(String key) {
            return true;
        }

        @Override
        public long ttl(String key) {
            return 100;
        }

        @Override
        public void close() {
        }
    }
}


