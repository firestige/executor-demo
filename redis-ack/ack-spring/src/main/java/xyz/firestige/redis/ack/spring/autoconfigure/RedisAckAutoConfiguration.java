package xyz.firestige.redis.ack.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.redis.ack.api.RedisAckService;
import xyz.firestige.redis.ack.core.DefaultRedisAckService;

/**
 * Redis ACK 服务自动配置
 *
 * @author AI
 * @since 1.0
 */
@AutoConfiguration
@ConditionalOnClass({RedisTemplate.class, RedisAckService.class})
@ConditionalOnProperty(prefix = "redis.ack", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisAckProperties.class)
public class RedisAckAutoConfiguration {

    /**
     * Redis ACK 服务 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisAckService redisAckService(
            RedisTemplate<String, String> redisTemplate,
            RestTemplate ackRestTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        return new DefaultRedisAckService(redisTemplate, ackRestTemplate, objectMapper, registry);
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
