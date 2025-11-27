package xyz.firestige.deploy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.config.properties.InfrastructureProperties;
import xyz.firestige.deploy.domain.task.CheckpointRepository;
import xyz.firestige.deploy.infrastructure.lock.TenantLockManager;
import xyz.firestige.deploy.infrastructure.lock.redis.RedisTenantLockManager;
import xyz.firestige.deploy.infrastructure.persistence.checkpoint.RedisCheckpointRepository;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TenantTaskIndexStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.redis.RedisPlanStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.redis.RedisTaskStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.redis.RedisTenantTaskIndexStore;

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
        public CheckpointRepository checkpointStore(StringRedisTemplate redisTemplate) {
            return new RedisCheckpointRepository(redisTemplate);
        }

        @Bean
        public TaskStateProjectionStore redisTaskStateProjectionStore(StringRedisTemplate redisTemplate) {
            return new RedisTaskStateProjectionStore(redisTemplate);
        }

        @Bean
        public PlanStateProjectionStore redisPlanStateProjectionStore(StringRedisTemplate redisTemplate) {
            return new RedisPlanStateProjectionStore(redisTemplate);
        }

        /**
         * Redis 租户任务索引存储
         */
        @Bean
        public TenantTaskIndexStore redisTenantTaskIndexStore(StringRedisTemplate redisTemplate) {
            return new RedisTenantTaskIndexStore(redisTemplate);
        }

        @Bean
        public TenantLockManager redisTenantLockManager(StringRedisTemplate redisTemplate) {
            return new RedisTenantLockManager(redisTemplate);
        }
    }
}
