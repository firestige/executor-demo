package xyz.firestige.infrastructure.redis.renewal.client.spi;

import xyz.firestige.infrastructure.redis.renewal.api.RedisClient;

/**
 * Redis 客户端提供者接口（SPI）
 *
 * <p>用于 SPI 机制动态加载 Redis 客户端实现。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>非 Spring 环境：通过 SPI 加载客户端</li>
 *   <li>多实现切换：运行时选择不同的客户端实现</li>
 *   <li>自定义实现：用户提供自己的客户端实现</li>
 * </ul>
 *
 * <h3>SPI 配置</h3>
 * <p>在 {@code META-INF/services/xyz.firestige.infrastructure.redis.renewal.client.spi.RedisClientProvider}
 * 文件中配置实现类全限定名：
 * <pre>
 * xyz.firestige.infrastructure.redis.renewal.client.spring.SpringRedisClientProvider
 * </pre>
 *
 * <h3>实现示例</h3>
 * <pre>{@code
 * public class SpringRedisClientProvider implements RedisClientProvider {
 *     public String getName() {
 *         return "spring";
 *     }
 *
 *     public RedisClient createClient() {
 *         // 创建 Spring Data Redis 客户端
 *         return new SpringRedisClient(redisTemplate);
 *     }
 * }
 * }</pre>
 *
 * @author T-018
 * @since 1.0.0
 * @see RedisClientLoader
 */
public interface RedisClientProvider {

    /**
     * 获取提供者名称
     *
     * <p>用于标识不同的客户端实现（如 "spring", "jedis", "lettuce"）。
     *
     * @return 提供者名称
     */
    String getName();

    /**
     * 创建 Redis 客户端实例
     *
     * @return Redis 客户端实例
     * @throws IllegalStateException 如果创建失败
     */
    RedisClient createClient();
}

