package xyz.firestige.deploy.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import xyz.firestige.deploy.infrastructure.lock.TenantLockManager;
import xyz.firestige.deploy.infrastructure.lock.redis.RedisTenantLockManager;
import xyz.firestige.deploy.infrastructure.persistence.projection.*;
import xyz.firestige.deploy.infrastructure.persistence.projection.redis.*;
import xyz.firestige.deploy.infrastructure.persistence.projection.memory.*;

/**
 * 执行器持久化自动配置
 * <p>
 * 职责：
 * - 根据配置自动装配 Redis 或 InMemory 实现
 * - 提供投影存储和租户锁的 Bean
 * - 支持条件注入，允许用户自定义实现
 * <p>
 * 配置示例（application.yml）：
 * <pre>
 * executor:
 *   persistence:
 *     store-type: redis  # redis 或 memory，默认 memory
 *     namespace: executor  # Redis Key 前缀，默认 executor
 *     projection-ttl: 7d  # 投影数据 TTL，默认 7 天
 *     lock-ttl: 2h30m  # 租户锁 TTL，默认 2.5 小时
 * </pre>
 *
 * @since T-016 投影型持久化
 */
@AutoConfiguration
@EnableConfigurationProperties(ExecutorPersistenceProperties.class)
public class ExecutorPersistenceAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorPersistenceAutoConfiguration.class);

    // ========== Redis Infrastructure ==========

    /**
     * Redis Template for Projections
     * <p>
     * 使用 StringRedisTemplate 简化序列化
     */
    @Bean(name = "executorProjectionRedisTemplate")
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "executorProjectionRedisTemplate")
    @ConditionalOnProperty(prefix = "executor.persistence", name = "store-type", havingValue = "redis")
    public RedisTemplate<String, String> executorProjectionRedisTemplate(RedisConnectionFactory factory) {
        logger.info("[AutoConfig] 创建 Redis Template for Executor Projections");
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }

    // ========== Task Projection Store ==========

    /**
     * Redis Task 投影存储
     */
    @Bean
    @ConditionalOnClass(RedisTemplate.class)
    @ConditionalOnMissingBean(TaskStateProjectionStore.class)
    @ConditionalOnProperty(prefix = "executor.persistence", name = "store-type", havingValue = "redis")
    public TaskStateProjectionStore redisTaskStateProjectionStore(
            RedisTemplate<String, String> executorProjectionRedisTemplate) {
        logger.info("[AutoConfig] 装配 Redis Task 投影存储");
        return new RedisTaskStateProjectionStore(executorProjectionRedisTemplate);
    }

    /**
     * 内存 Task 投影存储（Fallback）
     */
    @Bean
    @ConditionalOnMissingBean(TaskStateProjectionStore.class)
    public TaskStateProjectionStore inMemoryTaskStateProjectionStore() {
        logger.warn("[AutoConfig] 装配 InMemory Task 投影存储（Fallback）");
        return new InMemoryTaskStateProjectionStore();
    }

    // ========== Plan Projection Store ==========

    /**
     * Redis Plan 投影存储
     */
    @Bean
    @ConditionalOnClass(RedisTemplate.class)
    @ConditionalOnMissingBean(PlanStateProjectionStore.class)
    @ConditionalOnProperty(prefix = "executor.persistence", name = "store-type", havingValue = "redis")
    public PlanStateProjectionStore redisPlanStateProjectionStore(
            RedisTemplate<String, String> executorProjectionRedisTemplate) {
        logger.info("[AutoConfig] 装配 Redis Plan 投影存储");
        return new RedisPlanStateProjectionStore(executorProjectionRedisTemplate);
    }

    /**
     * 内存 Plan 投影存储（Fallback）
     */
    @Bean
    @ConditionalOnMissingBean(PlanStateProjectionStore.class)
    public PlanStateProjectionStore inMemoryPlanStateProjectionStore() {
        logger.warn("[AutoConfig] 装配 InMemory Plan 投影存储（Fallback）");
        return new InMemoryPlanStateProjectionStore();
    }

    // ========== Tenant Task Index Store ==========

    /**
     * Redis 租户任务索引存储
     */
    @Bean
    @ConditionalOnClass(RedisTemplate.class)
    @ConditionalOnMissingBean(TenantTaskIndexStore.class)
    @ConditionalOnProperty(prefix = "executor.persistence", name = "store-type", havingValue = "redis")
    public TenantTaskIndexStore redisTenantTaskIndexStore(
            RedisTemplate<String, String> executorProjectionRedisTemplate) {
        logger.info("[AutoConfig] 装配 Redis 租户任务索引存储");
        return new RedisTenantTaskIndexStore(executorProjectionRedisTemplate);
    }

    /**
     * 内存租户任务索引存储（Fallback）
     */
    @Bean
    @ConditionalOnMissingBean(TenantTaskIndexStore.class)
    public TenantTaskIndexStore inMemoryTenantTaskIndexStore() {
        logger.warn("[AutoConfig] 装配 InMemory 租户任务索引存储（Fallback）");
        return new InMemoryTenantTaskIndexStore();
    }

    // ========== Tenant Lock Manager ==========

    /**
     * Redis 租户锁管理器（分布式锁）
     */
    @Bean
    @ConditionalOnClass(RedisTemplate.class)
    @ConditionalOnMissingBean(TenantLockManager.class)
    @ConditionalOnProperty(prefix = "executor.persistence", name = "store-type", havingValue = "redis")
    public TenantLockManager redisTenantLockManager(
            RedisTemplate<String, String> executorProjectionRedisTemplate) {
        logger.info("[AutoConfig] 装配 Redis 租户锁管理器（分布式锁）");
        return new RedisTenantLockManager(executorProjectionRedisTemplate);
    }

    /**
     * 内存租户锁管理器（Fallback，仅支持单实例）
     */
    @Bean
    @ConditionalOnMissingBean(TenantLockManager.class)
    public TenantLockManager inMemoryTenantLockManager() {
        logger.warn("[AutoConfig] 装配 InMemory 租户锁管理器（Fallback，仅支持单实例）");
        return new InMemoryTenantLockManager();
    }

    /**
     * 内存租户锁管理器实现（用于测试和单实例场景）
     */
    private static class InMemoryTenantLockManager implements TenantLockManager {
        private final java.util.concurrent.ConcurrentHashMap<String, String> locks = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public boolean tryAcquire(xyz.firestige.deploy.domain.shared.vo.TenantId tenantId,
                                   xyz.firestige.deploy.domain.shared.vo.TaskId taskId,
                                   java.time.Duration ttl) {
            if (tenantId == null || taskId == null) {
                return false;
            }
            String key = tenantId.getValue();
            String value = taskId.getValue();
            return locks.putIfAbsent(key, value) == null;
        }

        @Override
        public void release(xyz.firestige.deploy.domain.shared.vo.TenantId tenantId) {
            if (tenantId != null) {
                locks.remove(tenantId.getValue());
            }
        }

        @Override
        public boolean exists(xyz.firestige.deploy.domain.shared.vo.TenantId tenantId) {
            return tenantId != null && locks.containsKey(tenantId.getValue());
        }
    }
}

