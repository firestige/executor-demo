package xyz.firestige.infrastructure.redis.renewal.api;

import java.util.Map;

/**
 * Key 生成策略接口
 *
 * <p>根据模板和上下文生成 Key 名称。
 *
 * <h3>预置实现</h3>
 * <ul>
 *   <li>{@code PlaceholderKeyGenerator} - 占位符替换（{var}）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * KeyGenerationStrategy strategy = new PlaceholderKeyGenerator();
 * String key = strategy.generateKey(
 *     "task:{tenantId}:{taskId}",
 *     Map.of("tenantId", "001", "taskId", "123")
 * );
 * // 结果: "task:001:123"
 * }</pre>
 *
 * @author T-018
 * @since 1.0.0
 */
public interface KeyGenerationStrategy {

    /**
     * 生成 Key
     *
     * @param template Key 模板
     * @param context 上下文变量
     * @return 生成的 Key
     */
    String generateKey(String template, Map<String, Object> context);

    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    String getName();
}

