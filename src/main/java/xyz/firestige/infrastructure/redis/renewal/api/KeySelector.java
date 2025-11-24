package xyz.firestige.infrastructure.redis.renewal.api;

import java.util.Collection;

/**
 * Key 选择器接口
 *
 * <p>决定哪些 Redis Key 需要续期。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>选择需要续期的 Key 集合</li>
 *   <li>支持静态列表、动态扫描、函数式选择等模式</li>
 * </ul>
 *
 * <h3>预置实现</h3>
 * <ul>
 *   <li>{@code StaticKeySelector} - 固定 Key 列表</li>
 *   <li>{@code PatternKeySelector} - 模式匹配扫描</li>
 *   <li>{@code PrefixKeySelector} - 前缀扫描</li>
 *   <li>{@code FunctionKeySelector} - 函数式选择</li>
 *   <li>{@code CompositeKeySelector} - 组合选择器</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 静态列表
 * KeySelector selector = new StaticKeySelector(List.of("key1", "key2"));
 *
 * // 模式匹配
 * KeySelector selector = new PatternKeySelector("deployment:*", redisClient);
 *
 * // 函数式选择
 * KeySelector selector = new FunctionKeySelector(ctx ->
 *     deploymentService.getActiveKeys()
 * );
 * }</pre>
 *
 * @author T-018
 * @since 1.0.0
 * @see RenewalContext
 */
public interface KeySelector {

    /**
     * 选择需要续期的 Key
     *
     * <p>每次续期前都会调用此方法，支持动态选择。
     *
     * @param context 续期上下文
     * @return Key 集合，不能为 {@code null}，可以为空
     * @throws IllegalStateException 如果选择过程失败
     */
    Collection<String> selectKeys(RenewalContext context);

    /**
     * 获取选择器名称
     *
     * @return 选择器名称
     */
    String getName();
}

