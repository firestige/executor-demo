package xyz.firestige.deploy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import xyz.firestige.deploy.config.properties.InfrastructureProperties;
import xyz.firestige.deploy.infrastructure.lock.TenantLockManager;
import xyz.firestige.deploy.infrastructure.lock.redis.RedisTenantLockManager;

@Configuration
@EnableConfigurationProperties(InfrastructureProperties.class)
public class InfrastructureConfiguration {

    @Configuration
    static class PersistenceConfiguration {
        @Bean
        public StringRedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
            return new StringRedisTemplate(redisConnectionFactory);
        }

        @Bean
        public TenantLockManager redisTenantLockManager(StringRedisTemplate redisTemplate) {
            return new RedisTenantLockManager(redisTemplate);
        }
    }
}
