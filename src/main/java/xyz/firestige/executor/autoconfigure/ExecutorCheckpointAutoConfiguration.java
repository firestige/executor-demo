package xyz.firestige.executor.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.executor.checkpoint.CheckpointStore;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.checkpoint.RedisCheckpointStore;
import xyz.firestige.executor.redis.RedisClient;
import xyz.firestige.executor.redis.SpringDataRedisClient;

@AutoConfiguration
@EnableConfigurationProperties(ExecutorCheckpointProperties.class)
public class ExecutorCheckpointAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "executorRedisTemplate")
    @ConditionalOnClass(RedisConnectionFactory.class)
    public RedisTemplate<String, byte[]> executorRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnClass(RedisTemplate.class)
    @ConditionalOnMissingBean
    public RedisClient redisClient(RedisTemplate<String, byte[]> executorRedisTemplate) {
        return new SpringDataRedisClient(executorRedisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(CheckpointStore.class)
    public CheckpointStore checkpointStore(ExecutorCheckpointProperties props, ApplicationContext ctx) {
        if (props.getStoreType() == ExecutorCheckpointProperties.StoreType.redis) {
            RedisClient client = ctx.getBean(RedisClient.class);
            return new RedisCheckpointStore(client, props.getNamespace(), props.getTtl());
        }
        return new InMemoryCheckpointStore();
    }
}

