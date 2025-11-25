package xyz.firestige.redis.renewal;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Redis 客户端抽象接口
 *
 * <p>定义 Redis 续期所需的最小操作集，支持多种 Redis 客户端实现。
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>抽象 Redis 操作，核心逻辑不依赖具体客户端</li>
 *   <li>支持同步和异步操作</li>
 *   <li>支持批量操作以减少网络开销</li>
 * </ul>
 *
 * <h3>实现</h3>
 * <ul>
 *   <li>{@code SpringRedisClient} - 基于 Spring Data Redis</li>
 *   <li>{@code JedisRedisClient} - 基于 Jedis（可选）</li>
 *   <li>{@code LettuceRedisClient} - 基于 Lettuce（可选）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 同步续期
 * boolean success = redisClient.expire("key1", 300);
 *
 * // 批量续期
 * Map<String, Boolean> results = redisClient.batchExpire(keys, 300);
 *
 * // 异步续期
 * CompletableFuture<Boolean> future = redisClient.expireAsync("key1", 300);
 * }</pre>
 *
 * @author T-018
 * @since 1.0.0
 */
public interface RedisClient {

    /**
     * 为 Key 设置过期时间（同步）
     *
     * @param key Redis Key
     * @param ttlSeconds 过期时间（秒）
     * @return {@code true} 设置成功，{@code false} Key 不存在
     */
    boolean expire(String key, long ttlSeconds);

    /**
     * 批量为 Key 设置过期时间（同步）
     *
     * <p>实现应使用 Pipeline 等批量操作优化性能。
     *
     * @param keys Redis Key 集合
     * @param ttlSeconds 过期时间（秒）
     * @return 续期结果映射（Key → 是否成功）
     */
    Map<String, Boolean> batchExpire(Collection<String> keys, long ttlSeconds);

    /**
     * 为 Key 设置过期时间（异步）
     *
     * <p>用于避免阻塞时间轮线程。
     *
     * @param key Redis Key
     * @param ttlSeconds 过期时间（秒）
     * @return CompletableFuture，完成时返回是否成功
     */
    CompletableFuture<Boolean> expireAsync(String key, long ttlSeconds);

    /**
     * 批量为 Key 设置过期时间（异步）
     *
     * @param keys Redis Key 集合
     * @param ttlSeconds 过期时间（秒）
     * @return CompletableFuture，完成时返回续期结果映射
     */
    CompletableFuture<Map<String, Boolean>> batchExpireAsync(
        Collection<String> keys,
        long ttlSeconds
    );

    /**
     * 扫描匹配模式的 Key
     *
     * <p>实现应使用 SCAN 命令避免阻塞 Redis。
     *
     * @param pattern 匹配模式（如 "prefix:*"）
     * @param count 每次扫描数量（建议值）
     * @return 匹配的 Key 集合
     */
    Collection<String> scan(String pattern, int count);

    /**
     * 检查 Key 是否存在
     *
     * @param key Redis Key
     * @return {@code true} 存在，{@code false} 不存在
     */
    boolean exists(String key);

    /**
     * 获取 Key 的剩余 TTL
     *
     * @param key Redis Key
     * @return TTL（秒），{@code -1} 表示永不过期，{@code -2} 表示不存在
     */
    long ttl(String key);

    /**
     * 关闭客户端连接
     *
     * <p>释放资源，服务关闭时调用。
     */
    void close();
}

