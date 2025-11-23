package xyz.firestige.deploy.infrastructure.lock.redis;

import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.infrastructure.lock.TenantLockManager;

import java.time.Duration;

/**
 * 租户锁 Redis 实现（分布式锁）
 * <p>
 * 使用 Redis SET NX 实现原子获取锁
 * TTL 自动释放，防止崩溃后泄漏
 *
 * @since T-016 投影型持久化
 */
public class RedisTenantLockManager implements TenantLockManager {

    private static final String KEY_PREFIX = "executor:lock:tenant:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisTenantLockManager(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(TenantId tenantId, TaskId taskId, Duration ttl) {
        if (tenantId == null || taskId == null || ttl == null) {
            return false;
        }

        String key = KEY_PREFIX + tenantId.getValue();
        String value = taskId.getValue().toString();

        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void release(TenantId tenantId) {
        if (tenantId == null) {
            return;
        }

        String key = KEY_PREFIX + tenantId.getValue();
        redisTemplate.delete(key);
    }

    @Override
    public boolean renew(TenantId tenantId, Duration additionalTtl) {
        if (tenantId == null || additionalTtl == null) {
            return false;
        }

        String key = KEY_PREFIX + tenantId.getValue();
        Boolean success = redisTemplate.expire(key, additionalTtl);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public boolean exists(TenantId tenantId) {
        if (tenantId == null) {
            return false;
        }

        String key = KEY_PREFIX + tenantId.getValue();
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
}

