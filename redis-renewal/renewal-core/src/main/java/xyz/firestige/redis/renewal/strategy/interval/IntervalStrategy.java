package xyz.firestige.redis.renewal.strategy.interval;

import xyz.firestige.redis.renewal.Named;
import xyz.firestige.redis.renewal.RenewalContext;

import java.time.Duration;

/**
 * 续期间隔策略接口
 *
 * <p>决定两次续期操作之间的时间间隔。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>计算下次续期的等待时间</li>
 *   <li>支持固定间隔、退避策略、自适应等模式</li>
 * </ul>
 *
 * <h3>预置实现</h3>
 * <ul>
 *   <li>{@code FixedIntervalStrategy} - 固定间隔</li>
 *   <li>{@code ExponentialBackoffStrategy} - 指数退避</li>
 *   <li>{@code AdaptiveIntervalStrategy} - 自适应（基于 TTL）</li>
 *   <li>{@code RandomizedIntervalStrategy} - 随机抖动</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 固定间隔：每 2 分钟续期一次
 * RenewalIntervalStrategy strategy = new FixedIntervalStrategy(Duration.ofMinutes(2));
 *
 * // 自适应间隔：TTL 的 50% 时续期
 * RenewalIntervalStrategy strategy = new AdaptiveIntervalStrategy(0.5);
 * }</pre>
 *
 * @author T-018
 * @since 1.0.0
 * @see RenewalContext
 */
@FunctionalInterface
public interface IntervalStrategy extends Named {

    /**
     * 计算下次续期的间隔时间
     *
     * @param context 续期上下文
     * @return 间隔时长，必须大于 0
     * @throws IllegalStateException 如果无法计算间隔
     */
    Duration nextInterval(RenewalContext context);
}

