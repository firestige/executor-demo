package xyz.firestige.infrastructure.redis.renewal.api;

/**
 * 停止条件接口
 *
 * <p>决定续期任务何时自动停止。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>判断是否应该停止续期任务</li>
 *   <li>支持时间、次数、外部信号等多种停止条件</li>
 * </ul>
 *
 * <h3>预置实现</h3>
 * <ul>
 *   <li>{@code NeverStopCondition} - 永不停止（需手动取消）</li>
 *   <li>{@code TimeBasedStopCondition} - 到达指定时间停止</li>
 *   <li>{@code CountBasedStopCondition} - 续期 N 次后停止</li>
 *   <li>{@code KeyNotExistsStopCondition} - Key 不存在时停止</li>
 *   <li>{@code ExternalSignalStopCondition} - 外部信号控制</li>
 *   <li>{@code CompositeStopCondition} - 组合条件（AND/OR）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 时间停止
 * StopCondition condition = new TimeBasedStopCondition(
 *     Instant.now().plus(Duration.ofHours(2))
 * );
 *
 * // 组合条件（任一满足即停止）
 * StopCondition condition = CompositeStopCondition.anyOf(
 *     new TimeBasedStopCondition(endTime),
 *     new CountBasedStopCondition(100)
 * );
 * }</pre>
 *
 * @author T-018
 * @since 1.0.0
 * @see RenewalContext
 */
public interface StopCondition {

    /**
     * 判断是否应该停止续期
     *
     * <p>每次续期后都会调用此方法检查。
     *
     * @param context 续期上下文
     * @return {@code true} 停止任务，{@code false} 继续
     */
    boolean shouldStop(RenewalContext context);

    /**
     * 获取条件名称
     *
     * @return 条件名称
     */
    String getName();
}

