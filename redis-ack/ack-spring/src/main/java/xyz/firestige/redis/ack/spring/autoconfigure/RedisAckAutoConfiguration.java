package xyz.firestige.redis.ack.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.redis.ack.api.AckMetricsRecorder;
import xyz.firestige.redis.ack.api.HttpClient;
import xyz.firestige.redis.ack.api.RedisAckService;
import xyz.firestige.redis.ack.api.RedisClient;
import xyz.firestige.redis.ack.spring.DefaultRedisAckService;
import xyz.firestige.redis.ack.spring.config.AckExecutorConfig;
import xyz.firestige.redis.ack.spring.http.RestTemplateHttpClient;
import xyz.firestige.redis.ack.spring.metrics.MicrometerAckMetricsRecorder;
import xyz.firestige.redis.ack.spring.redis.SpringRedisClient;

import java.util.concurrent.Executor;

/**
 * Redis ACK 服务自动配置
 *
 * @author AI
 * @since 1.0
 */
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, RedisAckService.class})
@ConditionalOnProperty(prefix = "redis.ack", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisAckProperties.class)
@Import(AckExecutorConfig.class)
public class RedisAckAutoConfiguration {

    /**
     * RedisClient Bean（基于 Spring StringRedisTemplate）
     */
    @Bean(name = "ackRedisClient")
    @ConditionalOnMissingBean(name = "ackRedisClient")
    public RedisClient ackRedisClient(StringRedisTemplate redisTemplate) {
        return new SpringRedisClient(redisTemplate);
    }

    /**
     * HttpClient Bean（基于 RestTemplate）
     */
    @Bean(name = "ackHttpClient")
    @ConditionalOnMissingBean(name = "ackHttpClient")
    public HttpClient ackHttpClient(@Qualifier("ackRestTemplate") RestTemplate ackRestTemplate) {
        return new RestTemplateHttpClient(ackRestTemplate);
    }

    /**
     * Redis ACK 服务 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisAckService redisAckService(
            @Qualifier("ackRedisClient") RedisClient ackRedisClient,
            @Qualifier("ackHttpClient") HttpClient ackHttpClient,
            ObjectMapper objectMapper,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Qualifier("ackVerifyExecutor") Executor ackVerifyExecutor) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        AckMetricsRecorder metricsRecorder = registry != null
            ? new MicrometerAckMetricsRecorder(registry)
            : AckMetricsRecorder.noop();

        return new DefaultRedisAckService(
            ackRedisClient,
            ackHttpClient,
            objectMapper,
            metricsRecorder,
            (java.util.concurrent.ExecutorService) ackVerifyExecutor
        );
    }

    /**
     * ACK 专用的 RestTemplate
     */
    @Bean(name = "ackRestTemplate")
    @ConditionalOnMissingBean(name = "ackRestTemplate")
    public RestTemplate ackRestTemplate(RedisAckProperties properties) {
        return new RestTemplateBuilder()
            .setConnectTimeout(properties.getHttp().getConnectTimeout())
            .setReadTimeout(properties.getHttp().getReadTimeout())
            .build();
    }

    /**
     * ObjectMapper（如果不存在则创建）
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * 健康检查指示器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    public RedisAckHealthIndicator redisAckHealthIndicator(RedisAckService ackService) {
        return new RedisAckHealthIndicator(ackService);
    }
}
