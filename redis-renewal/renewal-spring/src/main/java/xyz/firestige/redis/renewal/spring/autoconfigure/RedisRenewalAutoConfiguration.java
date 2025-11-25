package xyz.firestige.redis.renewal.spring.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.redis.renewal.AsyncRenewalExecutor;
import xyz.firestige.redis.renewal.KeyRenewalService;
import xyz.firestige.redis.renewal.RedisClient;
import xyz.firestige.redis.renewal.TimeWheelRenewalService;
import xyz.firestige.redis.renewal.metrics.RenewalMetricsCollector;
import xyz.firestige.redis.renewal.metrics.RenewalMetricsReporter;
import xyz.firestige.redis.renewal.spring.client.SpringRedisClient;
import xyz.firestige.redis.renewal.spring.metric.ActuatorHealthIndicator;

/**
 * Redis 续期服务自动配置
 */
@AutoConfiguration
@ConditionalOnClass({RedisTemplate.class, KeyRenewalService.class})
@ConditionalOnProperty(prefix = "redis.renewal", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisRenewalProperties.class)
public class RedisRenewalAutoConfiguration {

    /**
     * Redis 客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisClient redisClient(RedisTemplate<String, String> redisTemplate) {
        return new SpringRedisClient(redisTemplate);
    }

    /**
     * 异步执行器
     */
    @Bean
    @ConditionalOnMissingBean
    public AsyncRenewalExecutor asyncRenewalExecutor(
            RedisClient redisClient,
            RedisRenewalProperties properties) {
        return new AsyncRenewalExecutor(
            redisClient,
            properties.getExecutorThreadPoolSize(),
            properties.getExecutorQueueCapacity()
        );
    }

    /**
     * 续期服务
     */
    @Bean
    @ConditionalOnMissingBean
    public KeyRenewalService keyRenewalService(
            AsyncRenewalExecutor executor,
            RedisRenewalProperties properties) {
        return new TimeWheelRenewalService(
            executor,
            properties.getTimeWheel().getTickDuration(),
            properties.getTimeWheel().getTicksPerWheel()
        );
    }

    /**
     * 指标收集器
     */
    @Bean
    @ConditionalOnMissingBean
    public RenewalMetricsCollector renewalMetricsCollector() {
        return new RenewalMetricsCollector();
    }

    /**
     * 指标报告器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "redis.renewal", name = "metrics-report-interval")
    public RenewalMetricsReporter renewalMetricsReporter(
            RenewalMetricsCollector collector,
            RedisRenewalProperties properties) {
        RenewalMetricsReporter reporter = new RenewalMetricsReporter(
            collector,
            properties.getMetricsReportInterval()
        );
        reporter.start();
        return reporter;
    }

    /**
     * 健康检查指示器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    public ActuatorHealthIndicator renewalHealthIndicator(RenewalMetricsCollector collector) {
        return new ActuatorHealthIndicator(collector);
    }
}

