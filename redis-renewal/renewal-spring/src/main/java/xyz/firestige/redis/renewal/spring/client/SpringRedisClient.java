package xyz.firestige.redis.renewal.spring.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import xyz.firestige.redis.renewal.RedisClient;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Spring Data Redis 客户端适配器
 *
 * <p>基于 Spring Data Redis 实现 {@link RedisClient} 接口。
 *
 * <h3>特性</h3>
 * <ul>
 *   <li>使用 Pipeline 优化批量操作</li>
 *   <li>使用 SCAN 命令避免阻塞</li>
 *   <li>支持异步操作</li>
 * </ul>
 *
 * @author T-018
 * @since 1.0.0
 */
public class SpringRedisClient implements RedisClient {

    private static final Logger log = LoggerFactory.getLogger(SpringRedisClient.class);

    private final RedisTemplate<String, String> redisTemplate;

    public SpringRedisClient(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate cannot be null");
    }

    @Override
    public boolean expire(String key, long ttlSeconds) {
        try {
            Boolean result = redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("续期失败: key={}, ttl={}, error={}", key, ttlSeconds, e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Boolean> batchExpire(Collection<String> keys, long ttlSeconds) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Boolean> results = new LinkedHashMap<>();

        try {
            // 使用 Pipeline 批量执行
            List<Object> pipelineResults = redisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    for (String key : keys) {
                        connection.expire(key.getBytes(), ttlSeconds);
                    }
                    return null;
                }
            );

            // 处理结果
            int index = 0;
            for (String key : keys) {
                if (index < pipelineResults.size()) {
                    Object result = pipelineResults.get(index);
                    results.put(key, Boolean.TRUE.equals(result));
                } else {
                    results.put(key, false);
                }
                index++;
            }

        } catch (Exception e) {
            log.error("批量续期失败: keys={}, ttl={}, error={}", keys.size(), ttlSeconds, e.getMessage());
            // 失败时标记所有 Key 为失败
            for (String key : keys) {
                results.put(key, false);
            }
        }

        return results;
    }

    @Override
    public CompletableFuture<Boolean> expireAsync(String key, long ttlSeconds) {
        return CompletableFuture.supplyAsync(() -> expire(key, ttlSeconds));
    }

    @Override
    public CompletableFuture<Map<String, Boolean>> batchExpireAsync(
            Collection<String> keys,
            long ttlSeconds) {
        return CompletableFuture.supplyAsync(() -> batchExpire(keys, ttlSeconds));
    }

    @Override
    public Collection<String> scan(String pattern, int count) {
        Set<String> keys = new HashSet<>();

        try {
            ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(count)
                .build();

            RedisConnection connection = redisTemplate.getConnectionFactory()
                .getConnection();

            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
            } finally {
                // 确保连接释放
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }

        } catch (Exception e) {
            log.error("扫描 Key 失败: pattern={}, error={}", pattern, e.getMessage());
        }

        return keys;
    }

    @Override
    public boolean exists(String key) {
        try {
            Boolean result = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("检查 Key 存在性失败: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public long ttl(String key) {
        try {
            Long result = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return result != null ? result : -2;
        } catch (Exception e) {
            log.error("获取 TTL 失败: key={}, error={}", key, e.getMessage());
            return -2;
        }
    }

    @Override
    public void close() {
        // Spring 管理 RedisTemplate 生命周期，不需要手动关闭
        log.debug("SpringRedisClient close() called - managed by Spring");
    }
}

