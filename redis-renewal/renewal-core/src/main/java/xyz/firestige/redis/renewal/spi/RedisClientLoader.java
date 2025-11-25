package xyz.firestige.redis.renewal.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.redis.renewal.RedisClient;
import xyz.firestige.redis.renewal.RenewalContext;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Redis 客户端 SPI 加载器
 *
 * <p>用于非 Spring 环境，通过 Java SPI 机制加载客户端实现。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>非 Spring Boot 项目</li>
 *   <li>需要运行时动态选择客户端实现</li>
 *   <li>自定义客户端实现</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 加载第一个可用的客户端
 * RedisClient client = RedisClientLoader.loadFirst();
 *
 * // 加载指定名称的客户端
 * RedisClient client = RedisClientLoader.load("spring");
 * }</pre>
 *
 * @author T-018
 * @since 1.0.0
 */
public class RedisClientLoader {

    private static final Logger log = LoggerFactory.getLogger(RedisClientLoader.class);

    private static final ServiceLoader<RedisClientProvider> loader =
        ServiceLoader.load(RedisClientProvider.class);

    /**
     * 加载第一个可用的 Redis 客户端
     *
     * @return Redis 客户端实例
     * @throws IllegalStateException 如果未找到任何实现
     */
    public static RedisClient loadFirst() {
        Iterator<RedisClientProvider> iterator = loader.iterator();

        if (iterator.hasNext()) {
            RedisClientProvider provider = iterator.next();
            return createClient(provider);
        }

        throw new IllegalStateException(
            "未找到 RedisClientProvider 实现，请检查 META-INF/services 配置"
        );
    }

    /**
     * 加载指定名称的 Redis 客户端
     *
     * @param providerName 提供者名称
     * @return Redis 客户端实例
     * @throws IllegalArgumentException 如果未找到指定的实现
     */
    public static RedisClient load(String providerName) {
        for (RedisClientProvider provider : loader) {
            if (provider.getName().equalsIgnoreCase(providerName)) {
                return createClient(provider);
            }
        }

        throw new IllegalArgumentException(
            "未找到 RedisClientProvider: " + providerName
        );
    }

    private static RedisClient createClient(RedisClientProvider provider) {
        log.info("加载 Redis 客户端: {}", provider.getName());
        return provider.createClient();
    }

    /**
     * 重新加载 SPI 服务
     *
     * <p>在某些场景下（如类加载器变更）需要重新加载。
     */
    public static void reload() {
        loader.reload();
        log.debug("重新加载 RedisClientProvider SPI");
    }
}

