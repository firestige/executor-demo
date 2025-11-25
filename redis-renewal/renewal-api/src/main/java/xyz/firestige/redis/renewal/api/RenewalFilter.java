package xyz.firestige.redis.renewal.api;

import java.util.Collection;
import java.util.Map;

/**
 * 续期过滤器接口
 *
 * <p>在续期执行前后进行拦截和处理。
 *
 * <h3>预置实现</h3>
 * <ul>
 *   <li>{@code PassThroughFilter} - 直通过滤器（默认）</li>
 * </ul>
 *
 * @author T-018
 * @since 1.0.0
 */
public interface RenewalFilter {

    /**
     * 续期前拦截
     *
     * <p>可以过滤或修改 Key 列表。
     *
     * @param keys 待续期的 Key
     * @param ttlSeconds 待设置的 TTL
     * @return 过滤后的 Key（可以与输入不同）
     */
    Collection<String> beforeRenewal(Collection<String> keys, long ttlSeconds);

    /**
     * 续期后处理
     *
     * @param results 续期结果
     */
    void afterRenewal(Map<String, Boolean> results);

    /**
     * 获取过滤器名称
     *
     * @return 过滤器名称
     */
    String getName();
}

