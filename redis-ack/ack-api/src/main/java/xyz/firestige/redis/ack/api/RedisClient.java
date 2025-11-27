package xyz.firestige.redis.ack.api;

import java.time.Duration;
import java.util.Map;

/**
 * Redis 客户端抽象接口
 *
 * <p>定义 Redis ACK 所需的最小操作集，支持多种 Redis 客户端实现。
 *
 * <p><b>版本 2.0 更新</b>:
 * <ul>
 *   <li>新增 {@link #hmset(String, Map)} 支持多字段原子写入</li>
 * </ul>
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>抽象 Redis 操作，核心逻辑不依赖具体客户端</li>
 *   <li>支持 String、Hash、List、Set、ZSet 等数据类型</li>
 *   <li>支持 TTL 设置和 Pub/Sub 操作</li>
 * </ul>
 *
 * <h3>实现</h3>
 * <ul>
 *   <li>{@code SpringRedisClient} - 基于 Spring Data Redis (StringRedisTemplate)</li>
 *   <li>{@code JedisRedisClient} - 基于 Jedis（可选）</li>
 *   <li>{@code LettuceRedisClient} - 基于 Lettuce（可选）</li>
 * </ul>
 *
 * @author AI
 * @since 1.0
 */
public interface RedisClient {

    /**
     * SET 操作 - 设置 String 类型的值
     *
     * @param key Redis Key
     * @param value 值
     */
    void set(String key, String value);

    /**
     * SET 操作 - 设置 String 类型的值并指定 TTL
     *
     * @param key Redis Key
     * @param value 值
     * @param ttl 过期时间
     */
    void setWithTtl(String key, String value, Duration ttl);

    /**
     * HSET 操作 - 设置 Hash 字段
     *
     * @param key Redis Hash Key
     * @param field Hash 字段
     * @param value 值
     */
    void hset(String key, String field, String value);

    /**
     * HMSET 操作 - 批量设置多个 Hash 字段（原子操作）
     *
     * <p>使用场景：
     * <ul>
     *   <li>一次性写入多个配置字段</li>
     *   <li>确保多字段写入的原子性</li>
     *   <li>减少网络往返次数</li>
     * </ul>
     *
     * <p>示例:
     * <pre>{@code
     * Map<String, String> fields = new LinkedHashMap<>();
     * fields.put("config", configJson);
     * fields.put("metadata", metadataJson);
     * fields.put("status", "ACTIVE");
     * redisClient.hmset("deployment:tenant:123", fields);
     * }</pre>
     *
     * @param key Redis Hash Key
     * @param fields 字段-值映射（保持插入顺序）
     * @since 2.0
     */
    void hmset(String key, Map<String, String> fields);

    /**
     * EXPIRE 操作 - 为 Key 设置过期时间
     *
     * @param key Redis Key
     * @param ttl 过期时间
     */
    void expire(String key, Duration ttl);

    /**
     * LPUSH 操作 - 从左侧推入列表
     *
     * @param key Redis List Key
     * @param value 值
     */
    void lpush(String key, String value);

    /**
     * SADD 操作 - 添加集合成员
     *
     * @param key Redis Set Key
     * @param value 值
     */
    void sadd(String key, String value);

    /**
     * ZADD 操作 - 添加有序集合成员
     *
     * @param key Redis ZSet Key
     * @param value 值
     * @param score 分数
     */
    void zadd(String key, String value, double score);

    /**
     * PUBLISH 操作 - 发布消息到频道
     *
     * @param channel 频道名称
     * @param message 消息内容
     */
    void publish(String channel, String message);
}

