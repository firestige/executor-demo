package xyz.firestige.deploy.infrastructure.persistence.projection.redis;

import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.infrastructure.persistence.projection.TenantTaskIndexStore;

import java.time.Duration;

/**
 * TenantId → TaskId 索引 Redis 实现
 *
 * @since T-016 投影型持久化
 */
public class RedisTenantTaskIndexStore implements TenantTaskIndexStore {

    private static final String KEY_PREFIX = "executor:index:tenant:";
    private static final Duration TTL = Duration.ofDays(7);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisTenantTaskIndexStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void put(TenantId tenantId, TaskId taskId) {
        if (tenantId == null || taskId == null) {
            return;
        }

        String key = KEY_PREFIX + tenantId.getValue();
        redisTemplate.opsForValue().set(key, String.valueOf(taskId.getValue()), TTL);
    }

    @Override
    public TaskId get(TenantId tenantId) {
        if (tenantId == null) {
            return null;
        }

        String key = KEY_PREFIX + tenantId.getValue();
        String value = redisTemplate.opsForValue().get(key);

        return value != null ? TaskId.of(value) : null;
    }

    @Override
    public void remove(TenantId tenantId) {
        if (tenantId == null) {
            return;
        }

        String key = KEY_PREFIX + tenantId.getValue();
        redisTemplate.delete(key);
    }
}

