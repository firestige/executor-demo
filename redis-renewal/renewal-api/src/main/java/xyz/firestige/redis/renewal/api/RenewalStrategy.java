package xyz.firestige.redis.renewal.api;

import java.time.Duration;

/**
 * 续期策略接口
 *
 * <p>决定每次续期时设置的 TTL（过期时间）以及是否继续续期。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>计算下次续期的 TTL</li>
 *   <li>判断是否应该继续续期</li>
 * </ul>
 *
 * <h3>预置实现</h3>
 * <ul>
 *   <li>{@code FixedTtlStrategy} - 固定 TTL</li>
 *   <li>{@code DynamicTtlStrategy} - 动态计算 TTL</li>
 *   <li>{@code UntilTimeStrategy} - 续期至指定时间</li>
 *   <li>{@code MaxRenewalsStrategy} - 最多续期 N 次</li>
 *   <li>{@code ConditionalTtlStrategy} - 条件判断</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 固定 TTL
 * RenewalStrategy strategy = new FixedTtlStrategy(Duration.ofMinutes(5));
 *
 * // 动态 TTL
 * RenewalStrategy strategy = new DynamicTtlStrategy(ctx -> {
 *     return ctx.getRenewalCount() < 10
 *         ? Duration.ofMinutes(5)
 *         : Duration.ofMinutes(10);
 * });
 * }</pre>
 *
 * @author T-018
 * @since 1.0.0
 * @see RenewalContext
 */
public interface RenewalStrategy {

    /**
     * 计算下次续期的 TTL
     *
     * @param context 续期上下文（包含续期次数、上次时间等信息）
     * @return TTL 时长，必须大于 0
     * @throws IllegalStateException 如果无法计算 TTL
     */
    Duration calculateTtl(RenewalContext context);

    /**
     * 判断是否应该继续续期
     *
     * <p>如果返回 {@code false}，任务将自动停止。
     *
     * @param context 续期上下文
     * @return {@code true} 继续续期，{@code false} 停止
     */
    boolean shouldContinue(RenewalContext context);

    /**
     * 获取策略名称
     *
     * <p>用于日志和监控标识。
     *
     * @return 策略名称
     */
    String getName();
}

